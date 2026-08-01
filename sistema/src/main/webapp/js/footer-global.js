document.addEventListener('DOMContentLoaded', function () {
    const shell = document.getElementById('wrapper');
    if (!shell) return;

    let contentWrapper = document.getElementById('content-wrapper');
    if (!contentWrapper) {
        contentWrapper = document.createElement('div');
        contentWrapper.id = 'content-wrapper';
        contentWrapper.className = 'd-flex flex-column';

        const contenido = Array.from(shell.children).filter(function (elemento) {
            return !elemento.classList.contains('sidebar');
        });
        contenido.forEach(function (elemento) {
            contentWrapper.appendChild(elemento);
        });
        shell.appendChild(contentWrapper);
    }

    let footer = document.querySelector('footer.sticky-footer');
    if (!footer) {
        footer = document.createElement('footer');
        footer.className = 'sticky-footer bg-white';
    }

    footer.classList.add('mt-auto');
    footer.innerHTML =
        '<div class="container my-auto">' +
            '<div class="copyright text-center my-auto">' +
                '<span>Copyright &copy; ' + new Date().getFullYear() + '</span>' +
            '</div>' +
        '</div>';

    contentWrapper.appendChild(footer);
});
