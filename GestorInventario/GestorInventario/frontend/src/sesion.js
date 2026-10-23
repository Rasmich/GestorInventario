const claveSesion = 'gestorInventario.sesion'
const secciones = ['inicio', 'productos', 'categorias', 'proveedores', 'movimientos', 'usuarios']

export function recuperarSesion() {
  try {
    const sesion = JSON.parse(sessionStorage.getItem(claveSesion))
    const usuario = sesion?.usuario
    if (!usuario || typeof usuario.token !== 'string') return null
    const partes = usuario.token.split('.')
    if (partes.length !== 3 || partes.some((parte) => !parte)) return null
    const base64 = partes[1].replace(/-/g, '+').replace(/_/g, '/')
    const bytes = Uint8Array.from(atob(base64), (caracter) => caracter.charCodeAt(0))
    const claims = JSON.parse(new TextDecoder().decode(bytes))
    // Esta comprobación local no verifica la firma: la valida Spring Boot.
    if (!Number.isFinite(claims.exp) || claims.exp * 1000 <= Date.now()
        || claims.sub !== usuario.username || claims.uid !== usuario.id
        || claims.rol !== usuario.rol || typeof usuario.nombre !== 'string') return null
    const seccion = secciones.includes(sesion.seccion)
      && (sesion.seccion !== 'usuarios' || usuario.rol === 'ADMIN') ? sesion.seccion : 'inicio'
    return { usuario, seccion }
  } catch {
    return null
  }
}

export function guardarSesion(usuario, seccion) {
  try {
    if (usuario) {
      // Guardar solo los datos de sesión; nunca la contraseña del formulario.
      const { id, nombre, username, rol, token } = usuario
      sessionStorage.setItem(claveSesion, JSON.stringify({ usuario: { id, nombre, username, rol, token }, seccion }))
    } else {
      sessionStorage.removeItem(claveSesion)
    }
  } catch {
    // Si el navegador bloquea el almacenamiento, el login sigue funcionando en memoria.
  }
}
