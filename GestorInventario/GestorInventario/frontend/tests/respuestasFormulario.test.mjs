import assert from 'node:assert/strict'
import { test } from 'node:test'
import { fetchApi, leerRespuesta } from '../src/api.js'
import { erroresPorCampo, propiedadesCampo, enfocarError } from '../src/erroresFormulario.js'

const duplicados = [
  ['producto', 'productos', 'codigo', 'Ya existe un producto con ese código. Introduce un código diferente.'],
  ['categoria', 'categorias', 'nombre', 'Ya existe una categoría con ese nombre. Introduce un nombre diferente.'],
  ['proveedor', 'proveedores', 'cif', 'Ya existe un proveedor con ese CIF. Revisa el CIF introducido.'],
  ['usuario', 'usuarios', 'username', 'El nombre de usuario ya existe. Introduce uno diferente.'],
]

for (const [formulario, recurso, campo, mensaje] of duplicados) {
  for (const method of ['POST', 'PUT']) {
    test(`${method} ${recurso}: lectura común, campo explícito, ARIA y foco`, async (t) => {
      const datos = { detail: mensaje, erroresCampos: { [campo]: mensaje } }
      t.mock.method(globalThis, 'fetch', async () => Response.json(datos, { status: 409 }))
      await assert.rejects(async () => leerRespuesta(await fetchApi(`/api/${recurso}${method === 'PUT' ? '/2' : ''}`, { method })), (error) => {
        assert.equal(error.message, mensaje)
        assert.equal(error.status, 409)
        const errores = erroresPorCampo(formulario, error.message, error.campos)
        const id = `${formulario}-${campo}`
        assert.deepEqual(errores, { [id]: mensaje })
        assert.deepEqual(propiedadesCampo(errores, id), {
          'aria-invalid': true, 'aria-describedby': `${id}-error`,
        })
        let foco = ''
        enfocarError({ elements: [{ id, focus: () => { foco = id }, scrollIntoView() {} }] }, errores)
        assert.equal(foco, id)
        return true
      })
    })
  }
}

for (const status of [400, 422]) {
  test(`${status}: conserva todas las validaciones, incluso mensajes con separadores`, async () => {
    const mensajes = { nombre: 'Revisa este nombre · con separador.', cif: 'Introduce otro CIF.' }
    await assert.rejects(leerRespuesta(Response.json({ detail: 'Revisa los campos indicados.',
      errores: Object.values(mensajes), erroresCampos: mensajes }, { status })), (error) => {
      assert.equal(error.message, `Revisa los campos indicados. · ${Object.values(mensajes).join(' · ')}`)
      assert.deepEqual(erroresPorCampo('proveedor', error.message, error.campos), {
        'proveedor-nombre': mensajes.nombre, 'proveedor-cif': mensajes.cif,
      })
      return true
    })
  })
}

test('Login y Movimientos reciben los campos y detalles del lector común', async () => {
  for (const [formulario, campo, id] of [['login', 'username', 'username'], ['movimiento', 'cantidad', 'movimiento-cantidad']]) {
    await assert.rejects(leerRespuesta(Response.json({ detail: 'Revisa los campos indicados.',
      errores: ['Revisa este campo.'], erroresCampos: { [campo]: 'Revisa este campo.' } }, { status: 400 })), (error) => {
      assert.deepEqual(erroresPorCampo(formulario, error.message, error.campos), { [id]: 'Revisa este campo.' })
      return true
    })
  }
})

test('los campos explícitos se limitan a los controles conocidos del formulario', () => {
  assert.deepEqual(erroresPorCampo('producto', 'Error general', {
    password: 'No existe aquí', inventado: 'No existe aquí', nombre: 42, constructor: 'No es un control',
  }), {})
  assert.deepEqual(erroresPorCampo('usuario', 'Error', { password: 'Contraseña incorrecta' }), {
    'usuario-password': 'Contraseña incorrecta',
  })
  // El hook filtra además los controles no presentes en el modo actual.
})

for (const status of [400, 404, 409, 422]) {
  test(`${status} sin JSON: mensaje comprensible sin invalidar controles`, async () => {
    await assert.rejects(leerRespuesta(new Response('<html>proxy</html>', { status })), (error) => {
      assert.equal(error.status, status)
      assert.ok(error.message.length > 20)
      for (const formulario of ['producto', 'categoria', 'proveedor', 'usuario', 'movimiento', 'login'])
        assert.deepEqual(erroresPorCampo(formulario, error.message, error.campos), {})
      return true
    })
  })
}

test('404 y FK siguen siendo avisos generales y no atribuyen el problema a un campo', async () => {
  for (const detail of ['El producto no existe', 'La operacion entra en conflicto con relaciones existentes']) {
    await assert.rejects(leerRespuesta(Response.json({ detail }, { status: 409 })), (error) => {
      assert.equal(error.message, detail)
      assert.deepEqual(erroresPorCampo('producto', error.message, error.campos), {})
      return true
    })
  }
})

test('JSON inesperado en errores no provoca un fallo al unir mensajes', async () => {
  await assert.rejects(leerRespuesta(Response.json({ detail: {}, errores: 'texto' }, { status: 400 })), {
    message: 'Revisa los datos introducidos.', status: 400,
  })
})

test('respuestas correctas, 204 y JSON de éxito malformado', async () => {
  assert.deepEqual(await leerRespuesta(Response.json({ id: 1 })), { id: 1 })
  assert.equal(await leerRespuesta(new Response(null, { status: 204 })), null)
  await assert.rejects(leerRespuesta(new Response('<html>inesperado</html>')), {
    message: 'El servidor ha devuelto un formato de datos inesperado.',
  })
})
