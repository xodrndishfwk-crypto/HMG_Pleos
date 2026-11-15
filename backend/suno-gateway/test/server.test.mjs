import assert from "node:assert/strict";
import test from "node:test";
import { createGatewayServer, createMockProvider } from "../src/server.mjs";

const request = {
  requestId: "music-test",
  constellationId: "constellation-replay",
  sourceMode: "REPLAY",
  baseMelodyId: "melody-test",
  createdAtMs: 1_700_000_000_000,
  prompt: "Instrumental ambient arrangement",
  noteMidi: [60, 64, 67],
  noteStartBeats: [0, 1, 2],
  noteDurations: [1, 1, 2],
  chordMidiGroups: [],
  preferences: { genre: "AMBIENT", rhythm: "STRAIGHT", instruments: ["MUSIC_BOX"], tempoBpm: 88 },
};

async function withServer(run, token = "") {
  const server = createGatewayServer({ provider: createMockProvider(() => 1_700_000_000_000), sharedToken: token });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address();
  try {
    await run(`http://127.0.0.1:${port}`);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
}

test("idempotent request progresses to downloadable deterministic WAV", async () => {
  await withServer(async (baseUrl) => {
    const first = await fetch(`${baseUrl}/v1/music-jobs`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(request),
    });
    assert.equal(first.status, 202);
    const queued = await first.json();
    assert.equal(queued.status, "QUEUED");

    const duplicate = await fetch(`${baseUrl}/v1/music-jobs`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(request),
    });
    assert.equal(duplicate.status, 200);
    assert.equal((await duplicate.json()).jobId, queued.jobId);

    const generating = await fetch(`${baseUrl}/v1/music-jobs/${queued.jobId}`);
    assert.equal((await generating.json()).status, "GENERATING");
    const completed = await fetch(`${baseUrl}/v1/music-jobs/${queued.jobId}`);
    const succeeded = await completed.json();
    assert.equal(succeeded.status, "SUCCEEDED");
    const audio = await fetch(`${baseUrl}${succeeded.results[0].audioUrl}`);
    assert.equal(audio.status, 200);
    const bytes = Buffer.from(await audio.arrayBuffer());
    assert.equal(bytes.subarray(0, 4).toString(), "RIFF");
    assert.ok(bytes.length > 44);
  });
});

test("same id with different content is rejected and optional auth is enforced", async () => {
  await withServer(async (baseUrl) => {
    assert.equal((await fetch(`${baseUrl}/v1/music-jobs`, { method: "POST", body: JSON.stringify(request) })).status, 401);
    const headers = { "content-type": "application/json", "x-starlog-demo-token": "secret" };
    assert.equal((await fetch(`${baseUrl}/v1/music-jobs`, { method: "POST", headers, body: JSON.stringify(request) })).status, 202);
    const conflict = await fetch(`${baseUrl}/v1/music-jobs`, {
      method: "POST",
      headers,
      body: JSON.stringify({ ...request, prompt: "changed" }),
    });
    assert.equal(conflict.status, 409);
    assert.equal((await conflict.json()).error.code, "INVALID_INPUT");
  }, "secret");
});
