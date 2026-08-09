(() => {
  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
  if (reduceMotion.matches) document.body.classList.add("no-motion");

  const revealObserver = new IntersectionObserver((entries, observer) => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue;
      entry.target.classList.add("visible");
      observer.unobserve(entry.target);
    }
  }, { threshold: 0.13, rootMargin: "0px 0px -7%" });

  document.querySelectorAll(".reveal").forEach((element) => revealObserver.observe(element));

  const nav = document.querySelector("[data-nav]");
  const navMenu = document.querySelector(".global-nav nav");
  const navIndicator = navMenu?.querySelector(".nav-indicator");
  const navLinks = [...document.querySelectorAll(".global-nav nav a")];
  const sections = navLinks
    .map((link) => document.querySelector(link.getAttribute("href")))
    .filter(Boolean);

  const setNavState = () => nav?.classList.toggle("compact", window.scrollY > 24);
  setNavState();
  window.addEventListener("scroll", setNavState, { passive: true });

  let navProgressFrame = 0;
  let currentIndicatorIndex = -1;
  let navPhaseIndex = 0;
  let programmaticNavToken = 0;
  let programmaticNavTimer = 0;

  const setIndicator = (index, force = false) => {
    if (!navMenu || !navIndicator || !navLinks.length) return;
    const nextIndex = Math.max(0, Math.min(navLinks.length - 1, Math.round(index)));
    if (!force && nextIndex === currentIndicatorIndex) return;
    currentIndicatorIndex = nextIndex;
    navMenu.style.setProperty("--nav-index", String(nextIndex));
  };

  const getInitialNavIndex = () => {
    const marker = window.scrollY + Math.min(220, window.innerHeight * .32);
    let index = 0;
    while (index + 1 < sections.length && marker >= sections[index + 1].offsetTop) index += 1;
    return index;
  };

  const getPhaseNavIndex = () => {
    if (!sections.length) return 0;
    const markerOffset = Math.min(220, window.innerHeight * .32);
    const marker = window.scrollY + markerOffset;
    const forwardInset = 24;
    const backwardInset = 88;

    while (
      navPhaseIndex + 1 < sections.length &&
      marker >= sections[navPhaseIndex + 1].offsetTop + forwardInset
    ) navPhaseIndex += 1;

    while (
      navPhaseIndex > 0 &&
      marker < sections[navPhaseIndex].offsetTop - backwardInset
    ) navPhaseIndex -= 1;

    return navPhaseIndex;
  };

  const renderNavPhase = (index) => {
    navLinks.forEach((link, linkIndex) => link.classList.toggle("active", linkIndex === index));
    setIndicator(index);
  };

  const updateActiveNav = () => {
    navProgressFrame = 0;
    if (programmaticNavToken) return;
    renderNavPhase(getPhaseNavIndex());
  };

  const requestNavProgress = () => {
    if (navProgressFrame) return;
    navProgressFrame = requestAnimationFrame(updateActiveNav);
  };

  navLinks.forEach((link, index) => link.addEventListener("click", (event) => {
    const target = document.querySelector(link.getAttribute("href"));
    if (!target) return;
    event.preventDefault();
    const token = performance.now();
    programmaticNavToken = token;
    navPhaseIndex = index;
    renderNavPhase(index);
    window.history.replaceState(null, "", link.getAttribute("href"));
    window.scrollTo({
      top: Math.max(0, target.offsetTop),
      behavior: reduceMotion.matches ? "auto" : "smooth"
    });

    window.clearTimeout(programmaticNavTimer);
    const releaseProgrammaticNav = () => {
      if (programmaticNavToken !== token) return;
      programmaticNavToken = 0;
      navPhaseIndex = index;
      renderNavPhase(index);
    };
    programmaticNavTimer = window.setTimeout(releaseProgrammaticNav, reduceMotion.matches ? 40 : 3200);

    if ("onscrollend" in window) {
      window.addEventListener("scrollend", releaseProgrammaticNav, { once: true });
    }
  }));

  navPhaseIndex = getInitialNavIndex();
  renderNavPhase(navPhaseIndex);
  window.addEventListener("scroll", requestNavProgress, { passive: true });
  window.addEventListener("resize", () => {
    if (programmaticNavToken) return;
    navPhaseIndex = getInitialNavIndex();
    renderNavPhase(navPhaseIndex);
  });

  document.querySelectorAll("[data-gallery]").forEach((gallery) => {
    const shots = [...gallery.querySelectorAll("[data-shot]")];
    const count = gallery.querySelector("[data-count]");
    let index = Math.max(0, shots.findIndex((shot) => shot.classList.contains("active")));
    let timer;
    let pointerStart = null;

    const render = (nextIndex) => {
      index = (nextIndex + shots.length) % shots.length;
      shots.forEach((shot, shotIndex) => {
        const forward = (shotIndex - index + shots.length) % shots.length;
        const position = forward === 0 ? "active" : forward === 1 ? "right" : "left";
        shot.dataset.pos = position;
        shot.classList.toggle("active", position === "active");
        shot.setAttribute("aria-hidden", position === "active" ? "false" : "true");
      });
      if (count) count.textContent = `${index + 1} / ${shots.length}`;
    };

    const stopAutoplay = () => window.clearInterval(timer);
    const startAutoplay = () => {
      stopAutoplay();
      if (reduceMotion.matches || gallery.dataset.autoplay !== "true") return;
      timer = window.setInterval(() => render(index + 1), 5200);
    };

    gallery.querySelector("[data-prev]")?.addEventListener("click", () => {
      render(index - 1);
      startAutoplay();
    });
    gallery.querySelector("[data-next]")?.addEventListener("click", () => {
      render(index + 1);
      startAutoplay();
    });
    gallery.addEventListener("mouseenter", stopAutoplay);
    gallery.addEventListener("mouseleave", startAutoplay);
    gallery.addEventListener("focusin", stopAutoplay);
    gallery.addEventListener("focusout", startAutoplay);
    gallery.addEventListener("pointerdown", (event) => { pointerStart = event.clientX; });
    gallery.addEventListener("pointerup", (event) => {
      if (pointerStart === null) return;
      const delta = event.clientX - pointerStart;
      pointerStart = null;
      if (Math.abs(delta) < 42) return;
      render(index + (delta < 0 ? 1 : -1));
      startAutoplay();
    });

    const visibilityObserver = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) startAutoplay();
      else stopAutoplay();
    }, { threshold: 0.25 });
    visibilityObserver.observe(gallery);
    render(index);
  });

  const supportsScrollTimeline = CSS.supports?.("animation-timeline: view()") ?? false;
  if (!supportsScrollTimeline && !reduceMotion.matches) {
    const hero = document.querySelector(".hero");
    const heroCopy = document.querySelector(".hero-copy");
    const heroStage = document.querySelector(".hero-stage");
    let scheduled = false;
    const updateHero = () => {
      scheduled = false;
      if (!hero || !heroCopy || !heroStage) return;
      const travel = Math.max(1, hero.offsetHeight - window.innerHeight);
      const progress = Math.min(1, Math.max(0, window.scrollY / travel));
      heroCopy.style.opacity = String(1 - progress * .75);
      heroCopy.style.transform = `translateY(${-82 * progress}px) scale(${1 - .055 * progress})`;
      heroStage.style.transform = `translateY(${34 - 88 * progress}px) scale(${.94 + .08 * progress})`;
    };
    const requestHeroUpdate = () => {
      if (scheduled) return;
      scheduled = true;
      requestAnimationFrame(updateHero);
    };
    updateHero();
    window.addEventListener("scroll", requestHeroUpdate, { passive: true });
    window.addEventListener("resize", requestHeroUpdate);
  }
})();
