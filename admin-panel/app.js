/*
 * Panel de administración del padrón.
 *
 * JavaScript plano y sin dependencias, a propósito: el contenedor sirve estos
 * archivos tal cual, así que desplegar es copiar. Meter un paso de compilación
 * acá agregaría una cadena de herramientas al servidor para ahorrar unas pocas
 * líneas de código.
 */
(() => {
    'use strict';

    const API = window.CONFIG.API;

    /*
     * El token vive en sessionStorage y no en localStorage.
     *
     * Esto se usa en una computadora de escritorio que puede ser compartida.
     * En localStorage la sesión sobrevive a cerrar el navegador: el siguiente
     * que lo abra entra como administrador sin escribir nada. En
     * sessionStorage muere con la pestaña.
     */
    const sesion = {
        get token() { return sessionStorage.getItem('token'); },
        get usuario() { return sessionStorage.getItem('usuario'); },
        abrir(token, usuario) {
            sessionStorage.setItem('token', token);
            sessionStorage.setItem('usuario', usuario);
        },
        cerrar() { sessionStorage.clear(); },
    };

    const $ = (sel) => document.querySelector(sel);

    /*
     * Escapa antes de insertar en el documento.
     *
     * Los nombres y los documentos los escriben registradores en el campo, en
     * teléfonos, a veces apurados. Es entrada de usuario, y termina dentro de
     * innerHTML: sin esto, un nombre con una etiqueta HTML adentro se
     * ejecutaría en la pantalla del administrador.
     */
    const esc = (valor) => String(valor ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');

    const fecha = (iso) => {
        if (!iso) return '—';
        const d = new Date(iso);
        return Number.isNaN(d.getTime())
            ? '—'
            : d.toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
    };

    const vacio = (valor) => (valor === null || valor === undefined || valor === '' ? '—' : valor);

    // ==================================================================== API
    async function api(ruta, opciones = {}) {
        const cabeceras = { 'Content-Type': 'application/json' };
        if (sesion.token) cabeceras.Authorization = `Bearer ${sesion.token}`;

        let respuesta;
        try {
            respuesta = await fetch(API + ruta, { ...opciones, headers: cabeceras });
        } catch {
            throw new Error('No se pudo contactar al servidor. Revisá tu conexión.');
        }

        // La sesión venció o la cuenta se desactivó. Seguir mostrando el panel
        // con datos viejos sería peor que sacar a la persona al login.
        if (respuesta.status === 401) {
            sesion.cerrar();
            mostrarLogin('La sesión venció. Volvé a entrar.');
            throw new Error('Sesión vencida');
        }

        let cuerpo = null;
        try { cuerpo = await respuesta.json(); } catch { /* 204 y similares */ }

        if (!respuesta.ok) {
            throw new Error((cuerpo && cuerpo.error) || `Error ${respuesta.status}`);
        }
        return cuerpo;
    }

    function avisar(texto) {
        const caja = $('#aviso-flotante');
        caja.textContent = texto;
        caja.hidden = false;
        clearTimeout(avisar._t);
        avisar._t = setTimeout(() => { caja.hidden = true; }, 3200);
    }

    // ================================================================= Login
    $('#form-login').addEventListener('submit', async (e) => {
        e.preventDefault();
        const boton = $('#btn-entrar');
        const error = $('#login-error');
        error.hidden = true;
        boton.disabled = true;
        boton.textContent = 'Entrando…';

        try {
            const datos = await api('/api/auth/login-operador', {
                method: 'POST',
                body: JSON.stringify({
                    usuario: $('#usuario').value.trim(),
                    password: $('#password').value,
                }),
            });

            // Un registrador tiene credenciales válidas pero nada que hacer
            // acá: todos los endpoints del panel le van a responder 403. Es
            // más claro frenarlo en la puerta que dejarlo entrar a una
            // pantalla que le va a fallar entera.
            if (datos.rol !== 'admin') {
                throw new Error('Esta cuenta no es de administrador. Los registradores usan la aplicación del teléfono.');
            }

            sesion.abrir(datos.token, datos.usuario.usuario);
            $('#password').value = '';
            mostrarPanel();
        } catch (err) {
            error.textContent = err.message;
            error.hidden = false;
        } finally {
            boton.disabled = false;
            boton.textContent = 'Entrar';
        }
    });

    $('#btn-salir').addEventListener('click', () => {
        sesion.cerrar();
        mostrarLogin();
    });

    function mostrarLogin(mensaje) {
        $('#vista-panel').hidden = true;
        $('#vista-login').hidden = false;
        const error = $('#login-error');
        if (mensaje) { error.textContent = mensaje; error.hidden = false; }
        else { error.hidden = true; }
    }

    function mostrarPanel() {
        $('#vista-login').hidden = true;
        $('#vista-panel').hidden = false;
        $('#sesion-usuario').textContent = sesion.usuario || '';
        cambiarVista('conflictos');
    }

    // ================================================================= Tabs
    $('#tabs').addEventListener('click', (e) => {
        const tab = e.target.closest('.tab');
        if (tab) cambiarVista(tab.dataset.vista);
    });

    function cambiarVista(nombre) {
        document.querySelectorAll('.tab').forEach((t) => {
            t.classList.toggle('activa', t.dataset.vista === nombre);
        });
        $('#panel-conflictos').hidden = nombre !== 'conflictos';
        $('#panel-padron').hidden = nombre !== 'padron';
        $('#panel-registradores').hidden = nombre !== 'registradores';

        if (nombre === 'conflictos') cargarConflictos();
        if (nombre === 'padron') cargarPadron();
        if (nombre === 'registradores') cargarRegistradores();
    }

    // ============================================================ Conflictos
    let conflictosCargados = [];

    $('#filtro-estado').addEventListener('change', cargarConflictos);

    async function cargarConflictos() {
        const caja = $('#lista-conflictos');
        caja.innerHTML = '<div class="cargando">Cargando…</div>';

        try {
            const estado = $('#filtro-estado').value;
            const datos = await api(`/api/conflictos?estado=${estado}`);
            conflictosCargados = datos.conflictos || [];

            const pendientes = estado === 'PENDIENTE' ? conflictosCargados.length : null;
            const contador = $('#contador-conflictos');
            if (pendientes !== null) {
                contador.textContent = pendientes;
                contador.hidden = pendientes === 0;
            }

            if (conflictosCargados.length === 0) {
                caja.innerHTML = '<div class="vacio">No hay conflictos en este estado.</div>';
                return;
            }

            caja.innerHTML = conflictosCargados.map(fichaConflicto).join('');
        } catch (err) {
            caja.innerHTML = `<div class="vacio">${esc(err.message)}</div>`;
        }
    }

    function fichaConflicto(c) {
        const p = c.persona_existente || {};
        const esAlta = c.tipo === 'ALTA_DUPLICADA';
        const pill = esAlta
            ? '<span class="pill pill-error">Alta duplicada</span>'
            : '<span class="pill pill-alerta">Edición simultánea</span>';

        const estado = c.estado === 'PENDIENTE'
            ? ''
            : `<span class="pill pill-neutro">${esc(c.estado.toLowerCase())}</span>`;

        const nombre = [p.primer_nombre, p.primer_apellido].filter(Boolean).join(' ') || 'Registro sin nombre';

        return `
        <article class="ficha" data-id="${esc(c.id)}">
            <div class="ficha-datos">
                <div>${pill} ${estado}</div>
                <div class="ficha-titulo">${esc(nombre)}</div>
                <div class="ficha-meta">
                    ${esc(p.tipo_documento || '')} ${esc(p.numero_documento || '')}
                    · enviado por <strong>${esc(c.enviado_por)}</strong>
                    · ${esc(fecha(c.enviado_en))}
                </div>
            </div>
            <button class="btn btn-secundario btn-chico">Revisar</button>
        </article>`;
    }

    $('#lista-conflictos').addEventListener('click', (e) => {
        const ficha = e.target.closest('.ficha');
        if (!ficha) return;
        const conflicto = conflictosCargados.find((c) => c.id === ficha.dataset.id);
        if (conflicto) abrirConflicto(conflicto);
    });

    /* Campos comparables, en el orden en que se leen en una ficha. */
    const CAMPOS = [
        ['tipo_documento', 'Tipo de documento'],
        ['numero_documento', 'Número de documento'],
        ['primer_nombre', 'Primer nombre'],
        ['segundo_nombre', 'Segundo nombre'],
        ['primer_apellido', 'Primer apellido'],
        ['segundo_apellido', 'Segundo apellido'],
        ['fecha_nacimiento', 'Fecha de nacimiento'],
        ['sexo', 'Sexo'],
        ['telefono', 'Teléfono'],
    ];

    const normalizar = (v) => (v === null || v === undefined ? '' : String(v).trim());

    function abrirConflicto(c) {
        const actual = c.persona_existente || {};
        const entrante = c.datos_enviados || {};
        const resuelto = c.estado !== 'PENDIENTE';

        const diferencias = CAMPOS
            .map(([campo, etiqueta]) => ({
                campo,
                etiqueta,
                guardado: normalizar(actual[campo]),
                entrante: normalizar(entrante[campo]),
            }))
            .filter((f) => f.guardado !== f.entrante);

        const filas = diferencias.map((f) => `
            <tr>
                <td>${esc(f.etiqueta)}</td>
                <td class="valor-guardado">${esc(vacio(f.guardado))}</td>
                <td class="valor-entrante">${esc(vacio(f.entrante))}</td>
                <td class="elegir">
                    <input type="checkbox" data-campo="${esc(f.campo)}"
                           ${resuelto ? 'disabled' : ''}
                           aria-label="Usar el valor que llegó para ${esc(f.etiqueta)}">
                </td>
            </tr>`).join('');

        const tabla = diferencias.length === 0
            ? '<div class="vacio">El intento no aporta ningún dato distinto al que ya está guardado.</div>'
            : `<div class="tabla-wrap" style="margin-top:1.25rem">
                 <table class="comparacion">
                   <thead><tr>
                     <th>Campo</th><th>Guardado</th><th>Llegó</th><th>Usar</th>
                   </tr></thead>
                   <tbody>${filas}</tbody>
                 </table>
               </div>`;

        const esAlta = c.tipo === 'ALTA_DUPLICADA';

        const acciones = resuelto
            ? `<button class="btn btn-secundario" data-accion="cerrar">Cerrar</button>`
            : `
            <button class="btn btn-secundario" data-accion="cerrar">Cancelar</button>
            <button class="btn btn-peligro" data-accion="descartar">Descartar</button>
            ${esAlta ? '<button class="btn btn-secundario" data-accion="separar">Son personas distintas</button>' : ''}
            <button class="btn btn-primary" data-accion="fusionar">Aplicar lo marcado</button>`;

        const notaResuelta = resuelto && c.nota_resolucion
            ? `<p class="sub" style="margin-top:1rem">
                 <strong>${esc(c.estado.toLowerCase())}</strong> por ${esc(c.resuelto_por || '—')}
                 · ${esc(fecha(c.resuelto_en))}<br>${esc(c.nota_resolucion)}
               </p>`
            : '';

        $('#dialogo-cuerpo').innerHTML = `
        <div class="dialogo-inner">
            <h3>${esAlta ? 'Alta duplicada' : 'Edición simultánea'}</h3>
            <p class="sub">
                <strong>${esc(c.enviado_por)}</strong> envió estos datos y chocaron con lo que ya
                estaba guardado. Marcá los campos que quieras traer del intento; lo que dejes sin
                marcar queda como está.
            </p>
            ${notaResuelta}
            ${tabla}
            ${resuelto ? '' : `
            <div class="campo-form">
                <label for="nota">Nota (obligatoria para descartar)</label>
                <input id="nota" type="text" placeholder="Por qué se resolvió así">
            </div>`}
            <div class="dialogo-acciones">${acciones}</div>
        </div>`;

        $('#dialogo').showModal();
        $('#dialogo-cuerpo').dataset.conflicto = c.id;
    }

    $('#dialogo-cuerpo').addEventListener('click', async (e) => {
        const boton = e.target.closest('[data-accion]');
        if (!boton) return;

        const accion = boton.dataset.accion;
        const id = $('#dialogo-cuerpo').dataset.conflicto;

        if (accion === 'cerrar') { $('#dialogo').close(); return; }

        const nota = ($('#nota') && $('#nota').value.trim()) || '';

        try {
            if (accion === 'fusionar') {
                const campos = {};
                document.querySelectorAll('.comparacion input[type="checkbox"]:checked')
                    .forEach((chk) => {
                        const conflicto = conflictosCargados.find((c) => c.id === id);
                        campos[chk.dataset.campo] = conflicto.datos_enviados[chk.dataset.campo] ?? null;
                    });

                await api(`/api/conflictos/${id}/fusionar`, {
                    method: 'POST',
                    body: JSON.stringify({ campos, nota: nota || null }),
                });
                avisar(Object.keys(campos).length === 0
                    ? 'Conflicto cerrado sin cambios'
                    : `Se aplicaron ${Object.keys(campos).length} campo(s)`);
            }

            if (accion === 'descartar') {
                if (nota.length < 5) {
                    avisar('Escribí por qué se descarta antes de continuar');
                    return;
                }
                await api(`/api/conflictos/${id}/descartar`, {
                    method: 'POST',
                    body: JSON.stringify({ nota }),
                });
                avisar('Intento descartado. Los datos quedan guardados por si hace falta revisarlos.');
            }

            if (accion === 'separar') {
                pedirDocumentoCorregido(id);
                return;
            }

            $('#dialogo').close();
            cargarConflictos();
        } catch (err) {
            avisar(err.message);
        }
    });

    function pedirDocumentoCorregido(id) {
        const conflicto = conflictosCargados.find((c) => c.id === id);
        const enviados = conflicto.datos_enviados || {};

        $('#dialogo-cuerpo').innerHTML = `
        <div class="dialogo-inner">
            <h3>Son personas distintas</h3>
            <p class="sub">
                El intento entra como registro nuevo, a nombre de
                <strong>${esc(conflicto.enviado_por)}</strong>, con el documento corregido.
                Conserva el identificador que generó ese teléfono, así el dispositivo lo
                reconoce como propio al sincronizar.
            </p>
            <div class="campo-form">
                <label for="tipo-nuevo">Tipo de documento</label>
                <input id="tipo-nuevo" type="text" value="${esc(enviados.tipo_documento || 'CC')}">
            </div>
            <div class="campo-form">
                <label for="numero-nuevo">Número corregido</label>
                <input id="numero-nuevo" type="text" value="${esc(enviados.numero_documento || '')}">
            </div>
            <div class="dialogo-acciones">
                <button class="btn btn-secundario" data-accion="cerrar">Cancelar</button>
                <button class="btn btn-primary" id="btn-crear-nueva">Crear como nueva</button>
            </div>
        </div>`;

        $('#btn-crear-nueva').addEventListener('click', async () => {
            try {
                await api(`/api/conflictos/${id}/crear-como-nueva`, {
                    method: 'POST',
                    body: JSON.stringify({
                        tipo_documento: $('#tipo-nuevo').value.trim().toUpperCase(),
                        numero_documento: $('#numero-nuevo').value.trim(),
                    }),
                });
                avisar('Se creó como registro nuevo');
                $('#dialogo').close();
                cargarConflictos();
            } catch (err) {
                avisar(err.message);
            }
        });
    }

    // ================================================================ Padrón
    let personasCargadas = [];

    $('#buscar-persona').addEventListener('input', pintarPadron);

    async function cargarPadron() {
        const caja = $('#tabla-padron');
        caja.innerHTML = '<div class="cargando">Cargando…</div>';
        try {
            const datos = await api('/api/personas?limite=500');
            personasCargadas = datos.personas || [];
            $('#total-personas').textContent =
                `${datos.total} persona(s) registrada(s). Se muestran hasta 500.`;
            pintarPadron();
        } catch (err) {
            caja.innerHTML = `<div class="vacio">${esc(err.message)}</div>`;
        }
    }

    function pintarPadron() {
        const texto = $('#buscar-persona').value.trim().toLowerCase();
        const lista = personasCargadas.filter((p) => {
            if (!texto) return true;
            const nombre = [p.primer_nombre, p.segundo_nombre, p.primer_apellido, p.segundo_apellido]
                .filter(Boolean).join(' ').toLowerCase();
            return nombre.includes(texto) || String(p.numero_documento).includes(texto);
        });

        const caja = $('#tabla-padron');

        if (lista.length === 0) {
            caja.innerHTML = `<div class="vacio">${
                personasCargadas.length === 0
                    ? 'Todavía no hay personas registradas.'
                    : 'Ninguna coincide con la búsqueda.'
            }</div>`;
            return;
        }

        caja.innerHTML = `
        <div class="tabla-wrap">
          <table>
            <thead><tr>
              <th>Nombre</th><th>Documento</th><th>Nacimiento</th>
              <th>Teléfono</th><th>Registrado por</th><th>Ver</th>
            </tr></thead>
            <tbody>
              ${lista.map((p) => `
                <tr>
                  <td class="celda-principal">${esc(
                      [p.primer_nombre, p.segundo_nombre, p.primer_apellido, p.segundo_apellido]
                          .filter(Boolean).join(' ')
                  )}</td>
                  <td>${esc(p.tipo_documento)} ${esc(p.numero_documento)}</td>
                  <td class="celda-tenue">${esc(String(p.fecha_nacimiento).slice(0, 10))}</td>
                  <td class="celda-tenue">${esc(vacio(p.telefono))}</td>
                  <td><span class="pill pill-neutro">${esc(p.registrado_por)}</span></td>
                  <td>
                    <button class="btn btn-secundario btn-chico"
                            data-historial="${esc(p.id)}">Historial</button>
                  </td>
                </tr>`).join('')}
            </tbody>
          </table>
        </div>`;
    }

    $('#tabla-padron').addEventListener('click', (e) => {
        const boton = e.target.closest('[data-historial]');
        if (boton) verHistorial(boton.dataset.historial);
    });

    async function verHistorial(personaId) {
        const persona = personasCargadas.find((p) => p.id === personaId) || {};
        $('#dialogo-cuerpo').innerHTML = '<div class="dialogo-inner"><div class="cargando">Cargando…</div></div>';
        $('#dialogo').showModal();

        try {
            const entradas = await api(`/api/personas/${personaId}/historial`);

            const filas = entradas.map((h) => {
                const que = h.operacion === 'UPDATE'
                    ? `<strong>${esc(h.campo)}</strong>: ${esc(vacio(h.valor_anterior))} → ${esc(vacio(h.valor_nuevo))}`
                    : `<span class="pill pill-neutro">${esc(h.operacion)}</span>`;
                const origen = h.conflicto_id
                    ? '<span class="pill pill-alerta">de un conflicto</span>'
                    : '';
                return `<tr>
                    <td class="celda-tenue">v${esc(h.version)}</td>
                    <td>${que} ${origen}</td>
                    <td>${esc(h.realizado_por)}</td>
                    <td class="celda-tenue">${esc(fecha(h.realizado_en))}</td>
                </tr>`;
            }).join('');

            $('#dialogo-cuerpo').innerHTML = `
            <div class="dialogo-inner">
                <h3>${esc([persona.primer_nombre, persona.primer_apellido].filter(Boolean).join(' '))}</h3>
                <p class="sub">
                    Historial completo, campo por campo. No se recorta nunca:
                    ${entradas.length} entrada(s).
                </p>
                <div class="tabla-wrap" style="margin-top:1.25rem">
                  <table>
                    <thead><tr><th>Versión</th><th>Cambio</th><th>Quién</th><th>Cuándo</th></tr></thead>
                    <tbody>${filas}</tbody>
                  </table>
                </div>
                <div class="dialogo-acciones">
                    <button class="btn btn-secundario" data-accion="cerrar">Cerrar</button>
                </div>
            </div>`;
        } catch (err) {
            $('#dialogo-cuerpo').innerHTML = `
            <div class="dialogo-inner">
                <div class="vacio">${esc(err.message)}</div>
                <div class="dialogo-acciones">
                    <button class="btn btn-secundario" data-accion="cerrar">Cerrar</button>
                </div>
            </div>`;
        }
    }

    // ========================================================= Registradores
    async function cargarRegistradores() {
        const caja = $('#tabla-registradores');
        caja.innerHTML = '<div class="cargando">Cargando…</div>';
        try {
            const usuarios = await api('/api/usuarios');

            caja.innerHTML = `
            <div class="tabla-wrap">
              <table>
                <thead><tr>
                  <th>Usuario</th><th>Rol</th><th>Estado</th><th>Creado</th><th>Acciones</th>
                </tr></thead>
                <tbody>
                  ${usuarios.map((u) => `
                    <tr>
                      <td class="celda-principal">${esc(u.usuario)}</td>
                      <td><span class="pill pill-neutro">${esc(u.rol)}</span></td>
                      <td>${u.activo
                          ? '<span class="pill pill-exito">activo</span>'
                          : '<span class="pill pill-error">desactivado</span>'}</td>
                      <td class="celda-tenue">${esc(fecha(u.creado_en))}</td>
                      <td>
                        ${u.rol === 'admin' ? '' : `
                        <button class="btn btn-secundario btn-chico"
                                data-activo="${esc(u.id)}" data-valor="${!u.activo}">
                          ${u.activo ? 'Desactivar' : 'Reactivar'}
                        </button>
                        <button class="btn btn-secundario btn-chico"
                                data-clave="${esc(u.id)}" data-usuario="${esc(u.usuario)}">
                          Cambiar clave
                        </button>`}
                      </td>
                    </tr>`).join('')}
                </tbody>
              </table>
            </div>`;
        } catch (err) {
            caja.innerHTML = `<div class="vacio">${esc(err.message)}</div>`;
        }
    }

    $('#tabla-registradores').addEventListener('click', async (e) => {
        const activar = e.target.closest('[data-activo]');
        const clave = e.target.closest('[data-clave]');

        if (activar) {
            try {
                await api(`/api/usuarios/${activar.dataset.activo}`, {
                    method: 'PATCH',
                    body: JSON.stringify({ activo: activar.dataset.valor === 'true' }),
                });
                avisar('Cuenta actualizada');
                cargarRegistradores();
            } catch (err) { avisar(err.message); }
        }

        if (clave) formularioClave(clave.dataset.clave, clave.dataset.usuario);
    });

    $('#btn-nuevo-registrador').addEventListener('click', () => {
        $('#dialogo-cuerpo').innerHTML = `
        <div class="dialogo-inner">
            <h3>Nuevo registrador</h3>
            <p class="sub">
                Entregale estas credenciales en persona. Y avisale que
                <strong>tiene que entrar una vez con internet</strong> antes de salir al
                campo: sin ese primer inicio de sesión, el teléfono no lo deja entrar sin señal.
            </p>
            <div class="campo-form">
                <label for="nuevo-usuario">Usuario</label>
                <input id="nuevo-usuario" type="text" autocomplete="off">
            </div>
            <div class="campo-form">
                <label for="nueva-clave">Contraseña (mínimo 12 caracteres)</label>
                <input id="nueva-clave" type="text" autocomplete="off">
            </div>
            <div class="dialogo-acciones">
                <button class="btn btn-secundario" data-accion="cerrar">Cancelar</button>
                <button class="btn btn-primary" id="btn-crear-registrador">Crear</button>
            </div>
        </div>`;
        $('#dialogo').showModal();

        $('#btn-crear-registrador').addEventListener('click', async () => {
            try {
                await api('/api/usuarios', {
                    method: 'POST',
                    body: JSON.stringify({
                        usuario: $('#nuevo-usuario').value.trim(),
                        password: $('#nueva-clave').value,
                    }),
                });
                avisar('Registrador creado');
                $('#dialogo').close();
                cargarRegistradores();
            } catch (err) { avisar(err.message); }
        });
    });

    function formularioClave(id, usuario) {
        $('#dialogo-cuerpo').innerHTML = `
        <div class="dialogo-inner">
            <h3>Cambiar contraseña</h3>
            <p class="sub">
                Cuenta <strong>${esc(usuario)}</strong>. Después de cambiarla, esa persona
                necesita volver a entrar con internet para poder seguir usando la aplicación
                sin señal.
            </p>
            <div class="campo-form">
                <label for="clave-nueva">Contraseña nueva (mínimo 12 caracteres)</label>
                <input id="clave-nueva" type="text" autocomplete="off">
            </div>
            <div class="dialogo-acciones">
                <button class="btn btn-secundario" data-accion="cerrar">Cancelar</button>
                <button class="btn btn-primary" id="btn-guardar-clave">Guardar</button>
            </div>
        </div>`;
        $('#dialogo').showModal();

        $('#btn-guardar-clave').addEventListener('click', async () => {
            try {
                await api(`/api/usuarios/${id}`, {
                    method: 'PATCH',
                    body: JSON.stringify({ password: $('#clave-nueva').value }),
                });
                avisar('Contraseña actualizada');
                $('#dialogo').close();
            } catch (err) { avisar(err.message); }
        });
    }

    // ================================================================ Arranque
    if (sesion.token) mostrarPanel();
    else mostrarLogin();
})();
