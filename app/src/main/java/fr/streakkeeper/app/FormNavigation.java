package fr.streakkeeper.app;

/** One bounded, cancellable scroll; never submits or changes form values. */
final class FormNavigation {
    private FormNavigation() {}

    static final String CANCEL = "if(window.__streakCancelScroll)window.__streakCancelScroll();";
    static final String SCRIPT = """
        (function () {
            if (window.__streakCancelScroll) window.__streakCancelScroll();
            if (location.origin !== 'https://help.snapchat.com' ||
                location.pathname !== '/hc/fr-fr/requests/new') return;
            let timer, stopped = false, attempts = 0, previous = null, previousY = 0, stable = 0;
            const events = ['touchstart', 'pointerdown', 'wheel', 'keydown'];
            function cancel() {
                stopped = true;
                clearTimeout(timer);
                events.forEach(e => document.removeEventListener(e, cancel, true));
                window.removeEventListener('pagehide', cancel);
                if (window.__streakCancelScroll === cancel) delete window.__streakCancelScroll;
            }
            window.__streakCancelScroll = cancel;
            events.forEach(e => document.addEventListener(e, cancel, {capture:true, passive:true}));
            window.addEventListener('pagehide', cancel);
            function visible(el) {
                if (!el || !el.getClientRects().length) return false;
                for (let node = el; node && node.nodeType === 1; node = node.parentElement) {
                    const css = getComputedStyle(node);
                    if (css.display === 'none' || css.visibility === 'hidden' || Number(css.opacity) === 0) return false;
                }
                const rect = el.getBoundingClientRect();
                return rect.width > 0 && rect.height > 0;
            }
            function first(root, selector) {
                return Array.from(root.querySelectorAll(selector)).find(visible);
            }
            function target(form) {
                const consent = first(document, '#onetrust-banner-sdk, #CybotCookiebotDialog');
                if (consent) return consent;
                const error = first(form, '[aria-invalid="true"], .notification-error, [role="alert"]');
                if (error) return error;
                for (const field of form.querySelectorAll('input, select, textarea')) {
                    if (field.disabled) continue;
                    const invalid = (field.validity && !field.validity.valid) ||
                        (field.getAttribute('aria-required') === 'true' && !String(field.value || '').trim());
                    if (!invalid) continue;
                    if (visible(field)) return field;
                    // Zendesk sometimes replaces a required select with a visible control.
                    const group = field.closest('.form-field');
                    const control = group && first(group, '.nesty-input, [role="combobox"]');
                    if (control) return control;
                }
                const challenge = first(document, 'iframe[src*="/recaptcha/"][src*="/bframe"]');
                if (challenge) return challenge;
                const response = form.querySelector('[name="g-recaptcha-response"]');
                if (!response || !response.value) {
                    const captcha = first(form, 'iframe[src*="/recaptcha/"][src*="/anchor"]');
                    if (captcha) return captcha;
                }
                return first(form, 'button[type="submit"], input[type="submit"], button:not([type])');
            }
            function sample() {
                if (stopped) return;
                const form = document.querySelector('form#new_request');
                const el = form && target(form);
                attempts++;
                if (el) {
                    const y = el.getBoundingClientRect().top + window.scrollY;
                    stable = el === previous && Math.abs(y - previousY) < 2 ? stable + 1 : 0;
                    previous = el;
                    previousY = y;
                    if (stable >= 2) {
                        // Allow centering even when the submit button is at the page bottom.
                        if (el.matches('button[type="submit"], input[type="submit"], button:not([type])')) {
                            let space = document.getElementById('streak-scroll-space');
                            if (!space) {
                                space = document.createElement('div');
                                space.id = 'streak-scroll-space';
                                space.setAttribute('aria-hidden', 'true');
                                space.style.pointerEvents = 'none';
                                document.body.appendChild(space);
                            }
                            space.style.height = Math.ceil(window.innerHeight / 2) + 'px';
                        }
                        cancel();
                        el.scrollIntoView({behavior: 'instant', block:'center', inline:'nearest'});
                        console.log(__STREAK_SCROLL_DONE__);
                        return;
                    }
                } else {
                    previous = null;
                    stable = 0;
                }
                if (attempts >= 12) { cancel(); console.log(__STREAK_SCROLL_DONE__); return; }
                timer = setTimeout(sample, 250);
            }
            timer = setTimeout(sample, 250);
        })();
        """;
}
