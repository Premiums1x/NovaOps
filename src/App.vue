<template>
  <main class="boot-shell">
    <section class="boot-panel">
      <h1>NovaOps</h1>
      <p>Stage 0 initialized with Vue 3 + Vite + TypeScript.</p>
    </section>
  </main>
</template>

<style scoped>
.boot-shell {
  display: grid;
  min-height: 100vh;
  place-items: center;
}

.boot-panel {
  width: min(680px, 92vw);
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 32px;
  background: #fff;
}

h1 {
  margin: 0;
  font-size: 30px;
  color: #111827;
}

p {
  margin: 12px 0 0;
  color: #4b5563;
}
</style>
