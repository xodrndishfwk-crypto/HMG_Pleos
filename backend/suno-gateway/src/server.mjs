import http from "node:http";
import { createHash } from "node:crypto";
import { pathToFileURL } from "node:url";

const MAX_BODY_BYTES = 1_000_000;

function json(response, status, value) {
  const body = Buffer.from(JSON.stringify(value));
  response.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": body.length,
    "cache-control": "no-store",
  });
  response.end(body);
}

function failure(code, message, retryable = false, retryAfterMs = null) {
  return { error: { code, message, retryable, retryAfterMs } };
}

async function readJson(request) {
  const chunks = [];
  let length = 0;
  for await (const chunk of request) {
    length += chunk.length;
    if (length > MAX_BODY_BYTES) throw Object.assign(new Error("Request body is too large"), { status: 413 });
    chunks.push(chunk);
  }
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

function requireRequest(value) {
  if (!value || typeof value !== "object") throw new Error("JSON object required");
  for (const key of ["requestId", "constellationId", "sourceMode", "baseMelodyId", "prompt", "preferences"]) {
    if (!value[key]) throw new Error(`Missing ${key}`);
  }
  if (!Array.isArray(value.noteMidi) || !Array.isArray(value.noteStartBeats) || !Array.isArray(value.noteDurations)) {
    throw new Error("Melody note arrays are required");
  }
  if (value.noteMidi.length < 2 || value.noteMidi.length !== value.noteStartBeats.length ||
      value.noteMidi.length !== value.noteDurations.length) {
    throw new Error("Melody note arrays must have the same length and at least two notes");
  }
  if (!value.noteMidi.every((midi) => Number.isInteger(midi) && midi >= 0 && midi <= 127)) {
    throw new Error("MIDI values must be integers in 0..127");
  }
  if (!Number.isInteger(value.preferences.tempoBpm) || value.preferences.tempoBpm <= 0) {
    throw new Error("A positive integer tempoBpm is required");
  }
  if (!['LIVE', 'REPLAY'].includes(value.sourceMode)) throw new Error("Invalid sourceMode");
  return value;
}

function waveFor(request) {
  const sampleRate = 22_050;
  const samplesPerBeat = sampleRate * 60 / request.preferences.tempoBpm;
  const endBeat = Math.max(...request.noteStartBeats.map((start, index) => start + request.noteDurations[index]));
  const sampleCount = Math.max(1, Math.ceil(endBeat * samplesPerBeat));
  const mix = new Float64Array(sampleCount);
  for (let noteIndex = 0; noteIndex < request.noteMidi.length; noteIndex += 1) {
    const start = Math.floor(request.noteStartBeats[noteIndex] * samplesPerBeat);
    const length = Math.floor(request.noteDurations[noteIndex] * samplesPerBeat);
    const frequency = 440 * 2 ** ((request.noteMidi[noteIndex] - 69) / 12);
    for (let offset = 0; offset < length && start + offset < mix.length; offset += 1) {
      const progress = offset / Math.max(1, length);
      const envelope = (1 - progress) ** 1.8;
      const time = offset / sampleRate;
      mix[start + offset] += (Math.sin(2 * Math.PI * frequency * time) +
        0.25 * Math.sin(4 * Math.PI * frequency * time)) * envelope;
    }
  }
  let peak = 1;
  for (const value of mix) peak = Math.max(peak, Math.abs(value));
  const dataLength = sampleCount * 2;
  const buffer = Buffer.alloc(44 + dataLength);
  buffer.write("RIFF", 0);
  buffer.writeUInt32LE(36 + dataLength, 4);
  buffer.write("WAVEfmt ", 8);
  buffer.writeUInt32LE(16, 16);
  buffer.writeUInt16LE(1, 20);
  buffer.writeUInt16LE(1, 22);
  buffer.writeUInt32LE(sampleRate, 24);
  buffer.writeUInt32LE(sampleRate * 2, 28);
  buffer.writeUInt16LE(2, 32);
  buffer.writeUInt16LE(16, 34);
  buffer.write("data", 36);
  buffer.writeUInt32LE(dataLength, 40);
  for (let index = 0; index < sampleCount; index += 1) {
    buffer.writeInt16LE(Math.round(mix[index] / peak * 0.72 * 32767), 44 + index * 2);
  }
  return { buffer, durationMs: Math.round(sampleCount / sampleRate * 1000) };
}

export function createMockProvider(now = () => Date.now()) {
  const jobsById = new Map();
  const jobIdByRequest = new Map();
  return {
    name: "demo-mock",
    submit(rawRequest) {
      const request = requireRequest(rawRequest);
      const canonical = JSON.stringify(request);
      const existingJobId = jobIdByRequest.get(request.requestId);
      if (existingJobId) {
        const existing = jobsById.get(existingJobId);
        if (existing.canonical !== canonical) {
          return { statusCode: 409, body: failure("INVALID_INPUT", "Request ID identity conflict") };
        }
        return { statusCode: 200, body: snapshot(existing) };
      }
      const digest = createHash("sha256").update(canonical).digest("hex").slice(0, 20);
      const jobId = `mock-job-${digest}`;
      const resultId = `mock-result-${digest}`;
      const audio = waveFor(request);
      const job = { request, canonical, jobId, resultId, audio, polls: 0, createdAtMs: now(), updatedAtMs: now() };
      jobsById.set(jobId, job);
      jobIdByRequest.set(request.requestId, jobId);
      return { statusCode: 202, body: snapshot(job) };
    },
    get(jobId) {
      const job = jobsById.get(jobId);
      if (!job) return { statusCode: 404, body: failure("INVALID_INPUT", "Unknown job ID") };
      job.polls += 1;
      job.updatedAtMs = now();
      return { statusCode: 200, body: snapshot(job) };
    },
    audio(jobId, resultId) {
      const job = jobsById.get(jobId);
      if (!job || job.resultId !== resultId || job.polls < 2) return null;
      return job.audio.buffer;
    },
  };
}

function snapshot(job) {
  const status = job.polls === 0 ? "QUEUED" : job.polls === 1 ? "GENERATING" : "SUCCEEDED";
  const succeeded = status === "SUCCEEDED";
  return {
    request: job.request,
    provider: "demo-mock",
    jobId: job.jobId,
    status,
    results: succeeded ? [{
      id: job.resultId,
      audioUrl: `/v1/music-jobs/${job.jobId}/audio/${job.resultId}`,
      localFilePath: null,
      mimeType: "audio/wav",
      durationMs: job.audio.durationMs,
      model: "mock-melody-synth-v1",
      createdAtMs: job.createdAtMs,
    }] : [],
    selectedResultId: succeeded ? job.resultId : null,
    error: null,
    updatedAtMs: job.updatedAtMs,
  };
}

export function createUnavailableSunoPlatformProvider() {
  const unavailable = () => ({
    statusCode: 503,
    body: failure(
      "PROVIDER_UNAVAILABLE",
      "Official Suno Platform account REST contract is not configured; authenticated endpoint/schema access is required",
      false,
    ),
  });
  return { name: "suno-platform-unconfigured", submit: unavailable, get: unavailable, audio: () => null };
}

export function createGatewayServer({ provider = createMockProvider(), sharedToken = "" } = {}) {
  return http.createServer(async (request, response) => {
    try {
      if (request.url === "/health" && request.method === "GET") {
        return json(response, 200, { ok: true, provider: provider.name });
      }
      if (sharedToken && request.headers["x-starlog-demo-token"] !== sharedToken) {
        return json(response, 401, failure("AUTH_REQUIRED", "Gateway authorization required"));
      }
      if (request.url === "/v1/music-jobs" && request.method === "POST") {
        const result = provider.submit(await readJson(request));
        return json(response, result.statusCode, result.body);
      }
      const audioMatch = request.url?.match(/^\/v1\/music-jobs\/([^/]+)\/audio\/([^/]+)$/);
      if (audioMatch && request.method === "GET") {
        const audio = provider.audio(decodeURIComponent(audioMatch[1]), decodeURIComponent(audioMatch[2]));
        if (!audio) return json(response, 404, failure("INVALID_INPUT", "Audio result is not ready"));
        response.writeHead(200, {
          "content-type": "audio/wav",
          "content-length": audio.length,
          "cache-control": "private, max-age=300",
        });
        return response.end(audio);
      }
      const jobMatch = request.url?.match(/^\/v1\/music-jobs\/([^/]+)$/);
      if (jobMatch && request.method === "GET") {
        const result = provider.get(decodeURIComponent(jobMatch[1]));
        return json(response, result.statusCode, result.body);
      }
      return json(response, 404, failure("INVALID_INPUT", "Route not found"));
    } catch (error) {
      const status = error.status ?? (error instanceof SyntaxError ? 400 : 400);
      return json(response, status, failure("INVALID_INPUT", error.message ?? "Invalid request"));
    }
  });
}

export function providerFromEnvironment(environment = process.env) {
  if ((environment.MUSIC_PROVIDER ?? "mock") === "mock") return createMockProvider();
  if (environment.MUSIC_PROVIDER === "suno-platform") return createUnavailableSunoPlatformProvider();
  throw new Error(`Unsupported MUSIC_PROVIDER: ${environment.MUSIC_PROVIDER}`);
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const host = process.env.GATEWAY_HOST ?? "127.0.0.1";
  const port = Number.parseInt(process.env.GATEWAY_PORT ?? "8787", 10);
  const sharedToken = process.env.GATEWAY_SHARED_TOKEN ?? "";
  const server = createGatewayServer({ provider: providerFromEnvironment(), sharedToken });
  server.listen(port, host, () => {
    console.log(`StarLog music gateway listening on http://${host}:${port} (${process.env.MUSIC_PROVIDER ?? "mock"})`);
  });
}
