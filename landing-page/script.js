document.addEventListener('DOMContentLoaded', () => {
    const downloadBtn = document.getElementById('download-btn');
    const desktopWarning = document.getElementById('desktop-warning');

    // Función simple para detectar si es un dispositivo Android basado en el UserAgent
    const isAndroidDevice = () => {
        return /Android/i.test(navigator.userAgent);
    };

    downloadBtn.addEventListener('click', (e) => {
        e.preventDefault();

        if (!isAndroidDevice()) {
            // Si está en PC o iPhone/iOS, mostramos la alerta
            desktopWarning.innerHTML = '⚠️ Esta aplicación solo puede ser instalada en un dispositivo Android. No es compatible con iPhone o PC.';
            desktopWarning.classList.remove('hidden');
            
            // Efecto de shake en el botón para feedback visual
            downloadBtn.style.animation = 'shake 0.5s';
            setTimeout(() => {
                downloadBtn.style.animation = '';
            }, 500);
        } else {
            // Lógica para descargar el APK en el móvil
            desktopWarning.classList.add('hidden');
            
            // Simular inicio de descarga
            const originalText = downloadBtn.innerHTML;
            downloadBtn.innerHTML = 'Descargando... ⏳';
            downloadBtn.style.opacity = '0.8';
            
            setTimeout(() => {
                alert("La descarga del APK de WinPlay ha comenzado.");
                // Redirige al APK generado para iniciar la descarga
                window.location.href = './app-debug.apk';
                
                downloadBtn.innerHTML = originalText;
                downloadBtn.style.opacity = '1';
            }, 1500);
        }
    });
});

// Keyframes para el shake insertados dinámicamente o puedes ponerlos en el CSS
const style = document.createElement('style');
style.innerHTML = `
    @keyframes shake {
        0% { transform: translateX(0); }
        25% { transform: translateX(-5px); }
        50% { transform: translateX(5px); }
        75% { transform: translateX(-5px); }
        100% { transform: translateX(0); }
    }
`;
document.head.appendChild(style);
