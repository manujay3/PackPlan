import { mkdir, writeFile } from 'node:fs/promises';
const subjects = ['csc','ec','ma','st','eng','e','ch','py','bus','acc','mie','m','com','are'];
await mkdir('data', { recursive: true });
for (const subject of subjects) {
  const url = `https://catalog.ncsu.edu/course-descriptions/${subject}/`;
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${url}: ${response.status}`);
  await writeFile(`data/${subject}-source.html`, await response.text());
  console.log(`Downloaded ${subject}`);
}
for (const [id, path] of Object.entries({cs:'engineering/computer-science/computer-science-bs',econ:'management/economics/economics-ba'})) {
  const response = await fetch(`https://catalog.ncsu.edu/undergraduate/${path}/`);
  if (!response.ok) throw new Error(`Program ${id}: ${response.status}`);
  await writeFile(`data/${id}-source.html`, await response.text());
}
