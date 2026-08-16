const root = document.body;
const buttons = Array.from(document.querySelectorAll('[data-variant]'));
const concepts = Array.from(document.querySelectorAll('[data-concept]'));
const motionToggle = document.querySelector('#motionToggle');
const variantOrder = ['launch', 'liquid', 'editorial'];

function selectVariant(name, persist = true) {
  if (!variantOrder.includes(name)) name = 'launch';
  root.dataset.active = name;

  buttons.forEach((button) => {
    button.setAttribute('aria-selected', String(button.dataset.variant === name));
  });

  concepts.forEach((concept) => {
    const active = concept.dataset.concept === name;
    concept.hidden = !active;
    concept.classList.toggle('active', active);
  });

  const metaColors = {
    launch: '#f5f7fa',
    liquid: '#080b13',
    editorial: '#f2f0eb'
  };
  document.querySelector('meta[name="theme-color"]').content = metaColors[name];
  if (persist) localStorage.setItem('sleepdown-preview-variant', name);
}

buttons.forEach((button) => button.addEventListener('click', () => selectVariant(button.dataset.variant)));

motionToggle.addEventListener('click', () => {
  const reduced = root.classList.toggle('no-motion');
  motionToggle.setAttribute('aria-pressed', String(reduced));
  motionToggle.textContent = reduced ? '恢复动态效果' : '减少动态效果';
  localStorage.setItem('sleepdown-preview-motion', reduced ? 'reduced' : 'full');
});

document.addEventListener('keydown', (event) => {
  if (!['ArrowLeft', 'ArrowRight'].includes(event.key)) return;
  const current = variantOrder.indexOf(root.dataset.active || 'launch');
  const delta = event.key === 'ArrowRight' ? 1 : -1;
  selectVariant(variantOrder[(current + delta + variantOrder.length) % variantOrder.length]);
});

document.addEventListener('pointermove', (event) => {
  if (root.classList.contains('no-motion') || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  const visual = document.querySelector('.concept.active [data-tilt]');
  if (!visual) return;
  const x = event.clientX / innerWidth - .5;
  const y = event.clientY / innerHeight - .5;
  visual.style.setProperty('--pointer-x', x.toFixed(3));
  visual.style.setProperty('--pointer-y', y.toFixed(3));
});

document.querySelectorAll('[data-tilt]').forEach((visual) => {
  let index = 0;
  visual.dataset.stackIndex = '0';
  const count = visual.querySelector('[data-stack-count]');
  const render = () => {
    visual.dataset.stackIndex = String(index);
    if (count) count.textContent = `${index + 1} / 3`;
  };
  visual.querySelector('[data-stack-prev]')?.addEventListener('click', () => {
    index = (index + 2) % 3;
    render();
  });
  visual.querySelector('[data-stack-next]')?.addEventListener('click', () => {
    index = (index + 1) % 3;
    render();
  });
});

const savedVariant = localStorage.getItem('sleepdown-preview-variant') || 'launch';
const savedMotion = localStorage.getItem('sleepdown-preview-motion') === 'reduced';
if (savedMotion) {
  root.classList.add('no-motion');
  motionToggle.setAttribute('aria-pressed', 'true');
  motionToggle.textContent = '恢复动态效果';
}
selectVariant(savedVariant, false);
