document.addEventListener('DOMContentLoaded', () => {
    // 1. Navbar Scroll Effect (Elevation)
    const navbar = document.querySelector('nav');
    window.addEventListener('scroll', () => {
        if (window.scrollY > 20) {
            navbar.classList.add('scrolled');
        } else {
            navbar.classList.remove('scrolled');
        }
    });

    // 2. M3 Ripple Effect Implementation
    const buttons = document.querySelectorAll('.m3-btn');
    buttons.forEach(btn => {
        btn.addEventListener('mousedown', function (e) {
            const rect = btn.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const y = e.clientY - rect.top;

            const ripple = document.createElement('span');
            ripple.classList.add('ripple');
            
            // Set size based on the furthest corner
            const size = Math.max(rect.width, rect.height);
            ripple.style.width = ripple.style.height = `${size}px`;
            ripple.style.left = `${x - size / 2}px`;
            ripple.style.top = `${y - size / 2}px`;

            btn.appendChild(ripple);

            // Remove ripple after animation ends
            setTimeout(() => {
                ripple.remove();
            }, 600);
        });
    });

    // 3. Staggered Intersection Observer for Reveal Animations
    const observerOptions = {
        root: null,
        rootMargin: '0px',
        threshold: 0.15
    };

    const observer = new IntersectionObserver((entries, observer) => {
        entries.forEach((entry) => {
            if (entry.isIntersecting) {
                // Add active class with staggered delay based on index if inside a container
                const target = entry.target;
                
                // Optional: find index among siblings for staggering
                const parent = target.parentElement;
                if (parent && parent.classList.contains('features-grid') || parent.classList.contains('steps-container')) {
                    const siblings = Array.from(parent.children);
                    const index = siblings.indexOf(target);
                    target.style.transitionDelay = `${index * 100}ms`;
                }

                target.classList.add('active');
                observer.unobserve(target); // Only animate once
            }
        });
    }, observerOptions);

    const revealElements = document.querySelectorAll('.reveal');
    revealElements.forEach(el => observer.observe(el));
});
