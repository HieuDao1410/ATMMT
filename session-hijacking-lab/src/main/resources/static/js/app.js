(() => {
    'use strict';

    const passwordToggle = document.querySelector('[data-password-toggle]');
    if (passwordToggle) {
        const password = document.getElementById(passwordToggle.getAttribute('aria-controls'));
        passwordToggle.hidden = false;
        passwordToggle.addEventListener('click', () => {
            const visible = password.type === 'password';
            password.type = visible ? 'text' : 'password';
            passwordToggle.textContent = visible ? 'Ẩn' : 'Hiện';
            passwordToggle.setAttribute('aria-pressed', String(visible));
        });
    }

    const collection = document.querySelector('[data-notes-collection]');
    if (collection) {
        const notes = Array.from(collection.querySelectorAll('[data-note]'));
        const search = collection.querySelector('#note-search');
        const clear = collection.querySelector('.search-clear');
        const empty = collection.querySelector('[data-search-empty]');
        const status = collection.querySelector('[data-search-status]');
        // Match Vietnamese with or without accents, only within this user's rendered notes.
        const normalize = (text) => text.toLocaleLowerCase('vi').normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd');
        const searchable = notes.map((note) => normalize(
            note.querySelector('h3').textContent + ' ' + note.querySelector('.note-content').textContent));
        const filter = () => {
            const query = normalize(search.value.trim());
            let visible = 0;
            notes.forEach((note, index) => {
                const matches = searchable[index].includes(query);
                note.hidden = !matches;
                if (matches) visible++;
            });
            clear.hidden = search.value.length === 0;
            empty.hidden = visible > 0 || query.length === 0;
            status.textContent = query ? `Tìm thấy ${visible} trong ${notes.length} ghi chú.` : '';
        };
        const reset = () => {
            search.value = '';
            filter();
            search.focus();
        };
        if (notes.length > 0) collection.querySelector('[data-search-tools]').hidden = false;
        search.addEventListener('input', filter);
        clear.addEventListener('click', reset);
        collection.querySelector('[data-reset-search]').addEventListener('click', reset);
    }

    document.querySelectorAll('[data-submit-form]').forEach((form) => {
        const button = form.querySelector('button[type="submit"]');
        const label = button.querySelector('[data-button-text]');
        const initialText = label.textContent;
        form.addEventListener('submit', () => {
            button.disabled = true;
            button.setAttribute('aria-busy', 'true');
            label.textContent = button.dataset.loadingText;
        });
        // Restore controls when the browser brings a page back from its history cache.
        window.addEventListener('pageshow', () => {
            button.disabled = false;
            button.removeAttribute('aria-busy');
            label.textContent = initialText;
        });
    });
})();
