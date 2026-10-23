const campos = {
  producto: { codigo: 'codigo', nombre: 'nombre', descripcion: 'descripcion', precioCompra: 'precioCompra', precioVenta: 'precioVenta', stockMinimo: 'stockMinimo', categoriaId: 'categoriaId', proveedorId: 'proveedorId' },
  categoria: { nombre: 'nombre', descripcion: 'descripcion' },
  proveedor: { nombre: 'nombre', cif: 'cif', telefono: 'telefono', email: 'email', direccion: 'direccion' },
  movimiento: { productoId: 'producto', tipo: 'tipo', cantidad: 'cantidad' },
  usuario: { nombre: 'nombre', username: 'username', password: 'password', rol: 'rol' },
  login: { username: 'username', password: 'password' },
}
const etiquetas = {
  'Código del producto': 'codigo', Nombre: 'nombre', Descripción: 'descripcion',
  'Precio de compra': 'precioCompra', 'Precio de venta': 'precioVenta', 'Stock mínimo': 'stockMinimo',
  Categoría: 'categoriaId', Proveedor: 'proveedorId', Producto: 'productoId',
  'Tipo de movimiento': 'tipo', Cantidad: 'cantidad', CIF: 'cif', Teléfono: 'telefono',
  'Correo electrónico': 'email', Dirección: 'direccion', 'Nombre de usuario': 'username',
  Contraseña: 'password', Rol: 'rol',
}
const duplicados = {
  'Ya existe un producto con ese código. Introduce un código diferente.': ['producto', 'codigo'],
  'Ya existe una categoría con ese nombre. Introduce un nombre diferente.': ['categoria', 'nombre'],
  'Ya existe un proveedor con ese CIF. Revisa el CIF introducido.': ['proveedor', 'cif'],
  'El nombre de usuario ya existe. Introduce uno diferente.': ['usuario', 'username'],
}

// Only explicit field prefixes or known duplicate messages identify a field safely.
export function erroresPorCampo(formulario, mensaje, erroresCampos) {
  const resultado = {}
  if (erroresCampos && typeof erroresCampos === 'object' && !Array.isArray(erroresCampos)) {
    for (const [campo, texto] of Object.entries(erroresCampos)) {
      const sufijo = Object.hasOwn(campos[formulario] ?? {}, campo) ? campos[formulario][campo] : undefined
      if (sufijo && typeof texto === 'string' && texto)
        resultado[formulario === 'login' ? sufijo : `${formulario}-${sufijo}`] = texto
    }
    return resultado
  }
  for (const parte of mensaje.split(' · ')) {
    const prefijo = parte.split(':', 1)[0]
    const campo = etiquetas[prefijo] ?? prefijo
    const sufijo = campos[formulario]?.[campo]
    if (sufijo && parte.startsWith(`${prefijo}:`)) {
      resultado[formulario === 'login' ? sufijo : `${formulario}-${sufijo}`] = parte
    }
    const duplicado = duplicados[parte]
    if (duplicado?.[0] === formulario) resultado[`${formulario}-${duplicado[1]}`] = parte
  }
  return resultado
}

export function propiedadesCampo(errores, id, ayuda) {
  return { 'aria-invalid': errores[id] ? true : undefined,
    'aria-describedby': [ayuda, errores[id] ? `${id}-error` : ''].filter(Boolean).join(' ') || undefined }
}

export function enfocarError(formulario, errores, aviso) {
  const primero = Array.from(formulario?.elements ?? []).find((campo) => errores[campo.id]
    && !campo.disabled && !campo.matches?.(':disabled'))
  if (primero) {
    primero.focus({ preventScroll: true })
    primero.scrollIntoView({ block: 'center', behavior: 'auto' })
  } else {
    aviso?.scrollIntoView({ block: 'nearest', behavior: 'auto' })
  }
}
