package fr.streakkeeper.app;

/** Inspect visible first-party page content; never interact with a CAPTCHA or submit a form. */
final class SubmissionPage {
    private SubmissionPage() {}

    static final String SCRIPT = """
        (function () {
            if (!document.body) return 0;
            let t = (document.body.innerText || '').toLowerCase().normalize('NFD')
                .replace(/[\u0300-\u036f]/g, '').replace(/\\s+/g, ' ');
            if (/nous avons (?:bien )?(?:pris en compte|recu).{0,100}(?:demande|requete)/.test(t) ||
                /(?:votre|ta) (?:demande|requete).{0,100}(?:bien )?(?:prise en compte|recue)/.test(t)) return 2;
            const frames = document.querySelectorAll('iframe[src*="/recaptcha/"][src*="/bframe"], iframe[src*="/recaptcha/"][src*="/anchor"]');
            for (const frame of frames) {
                const rect = frame.getBoundingClientRect();
                let visible = rect.width > 0 && rect.height > 0;
                for (let node = frame; visible && node && node.nodeType === 1; node = node.parentElement) {
                    const style = getComputedStyle(node);
                    if (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity) === 0) visible = false;
                }
                if (visible && frame.src.includes('/bframe')) return 1;
            }
            return 0;
        })()
        """;
}
