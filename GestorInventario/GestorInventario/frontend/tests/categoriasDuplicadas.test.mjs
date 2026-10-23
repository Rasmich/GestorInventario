import assert from 'node:assert/strict'
import { test } from 'node:test'
import { fetchApi } from '../src/api.js'
import { erroresPorCampo, propiedadesCampo, enfocarError } from '../src/erroresFormulario.js'

for (const method of ['POST', 'PUT']) {
  test(`${method}: conserva el conflicto de categoría, describe Nombre y dirige el foco`, async (t) => {
    const mensaje = 'Ya existe una categoría con ese nombre. Introduce un nombre diferente.'
    t.mock.method(globalThis, 'fetch', async () => Response.json({ detail: mensaje }, { status: 409 }))
    const respuesta = await fetchApi(method === 'POST' ? '/api/categorias' : '/api/categorias/2', {
      method, body: JSON.stringify({ nombre: '1234' }),
    })
    assert.equal(respuesta.status, 409)
    const { detail } = await respuesta.json()
    const errores = erroresPorCampo('categoria', detail)
    assert.deepEqual(errores, { 'categoria-nombre': mensaje })
    assert.deepEqual(propiedadesCampo(errores, 'categoria-nombre'), {
      'aria-invalid': true, 'aria-describedby': 'categoria-nombre-error',
    })
    assert.equal(propiedadesCampo(errores, 'categoria-descripcion')['aria-invalid'], undefined)
    let enfocado = false
    enfocarError({ elements: [{ id: 'categoria-nombre', focus: () => { enfocado = true },
      scrollIntoView() {} }] }, errores)
    assert.equal(enfocado, true)
  })
}
