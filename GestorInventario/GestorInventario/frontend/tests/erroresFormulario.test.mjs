import assert from 'node:assert/strict'
import { test } from 'node:test'
import { enfocarError, erroresPorCampo } from '../src/erroresFormulario.js'

test('asocia validaciones de API únicamente a los campos del formulario correspondiente', () => {
  const mensaje = 'Revisa los campos indicados. · Precio de compra: El valor debe ser mayor o igual que 0.00. · Categoría: Este campo es obligatorio.'
  assert.deepEqual(erroresPorCampo('producto', mensaje), {
    'producto-precioCompra': 'Precio de compra: El valor debe ser mayor o igual que 0.00.',
    'producto-categoriaId': 'Categoría: Este campo es obligatorio.',
  })
  assert.deepEqual(erroresPorCampo('login', mensaje), {})
})

for (const [formulario, mensaje, id] of [
  ['producto', 'Ya existe un producto con ese código. Introduce un código diferente.', 'producto-codigo'],
  ['categoria', 'Ya existe una categoría con ese nombre. Introduce un nombre diferente.', 'categoria-nombre'],
  ['proveedor', 'Ya existe un proveedor con ese CIF. Revisa el CIF introducido.', 'proveedor-cif'],
  ['usuario', 'El nombre de usuario ya existe. Introduce uno diferente.', 'usuario-username'],
]) {
  test(`asocia el duplicado de ${formulario} conservando el mensaje original`, () => {
    assert.deepEqual(erroresPorCampo(formulario, mensaje), { [id]: mensaje })
  })
}

test('identifica email, contraseña y cantidad por el prefijo explícito', () => {
  assert.deepEqual(erroresPorCampo('proveedor', 'Correo electrónico: Introduce un correo electrónico válido.'), {
    'proveedor-email': 'Correo electrónico: Introduce un correo electrónico válido.',
  })
  assert.deepEqual(erroresPorCampo('usuario', 'Contraseña: La longitud debe estar entre 8 y 72 caracteres.'), {
    'usuario-password': 'Contraseña: La longitud debe estar entre 8 y 72 caracteres.',
  })
  assert.deepEqual(erroresPorCampo('movimiento', 'Cantidad: Introduce un número mayor que cero.'), {
    'movimiento-cantidad': 'Cantidad: Introduce un número mayor que cero.',
  })
})

for (const mensaje of [
  'No se ha podido conectar con el servidor. Inténtalo de nuevo más tarde.',
  'Tu sesión ha caducado o ya no es válida. Inicia sesión de nuevo.',
  'No tienes permisos para realizar esta operación.',
  'Usuario o contraseña incorrectos.',
  'No hay suficiente stock',
  'La operación entra en conflicto con relaciones existentes',
]) {
  test(`no marca un campo por el error general: ${mensaje}`, () => {
    for (const formulario of ['producto', 'categoria', 'proveedor', 'movimiento', 'usuario', 'login']) {
      assert.deepEqual(erroresPorCampo(formulario, mensaje), {})
    }
  })
}

test('enfoca el primer campo incorrecto por orden del formulario y lo hace visible', () => {
  const acciones = []
  const campo = (id) => ({ id, focus: () => acciones.push(`foco:${id}`),
    scrollIntoView: () => acciones.push(`visible:${id}`) })
  const primero = campo('producto-codigo')
  const segundo = campo('producto-nombre')
  enfocarError({ elements: [primero, segundo] }, {
    'producto-nombre': 'Error en nombre', 'producto-codigo': 'Error en código',
  }, { scrollIntoView: () => acciones.push('aviso') })
  assert.deepEqual(acciones, ['foco:producto-codigo', 'visible:producto-codigo'])
})

test('un error de servidor muestra el aviso sin enfocar ni invalidar un campo', () => {
  let visible = false
  enfocarError({ elements: [{ id: 'username', focus: () => assert.fail('No debe enfocar un campo') }] }, {}, {
    scrollIntoView: () => { visible = true },
  })
  assert.equal(visible, true)
})

test('no enfoca controles deshabilitados por un fieldset', () => {
  let visible = false
  enfocarError({ elements: [{ id: 'usuario-password', matches: () => true,
    focus: () => assert.fail('El campo está deshabilitado') }] }, { 'usuario-password': 'Error' }, {
    scrollIntoView: () => { visible = true },
  })
  assert.equal(visible, true)
})
