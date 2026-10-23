import assert from 'node:assert/strict'
import { test } from 'node:test'
import { fetchApi } from '../src/api.js'
import { erroresPorCampo, propiedadesCampo, enfocarError } from '../src/erroresFormulario.js'

for (const method of ['POST', 'PUT']) {
  test(`${method}: conflicto de producto asociado a Código con descripción y foco`, async (t) => {
    const mensaje = 'Ya existe un producto con ese código. Introduce un código diferente.'
    t.mock.method(globalThis, 'fetch', async () => Response.json({ detail: mensaje }, { status: 409 }))
    const respuesta = await fetchApi(method === 'POST' ? '/api/productos' : '/api/productos/2', {
      method, body: JSON.stringify({ codigo: 'PER002' }),
    })
    assert.equal(respuesta.status, 409)
    const { detail } = await respuesta.json()
    const errores = erroresPorCampo('producto', detail)
    assert.deepEqual(errores, { 'producto-codigo': mensaje })
    assert.deepEqual(propiedadesCampo(errores, 'producto-codigo'), {
      'aria-invalid': true, 'aria-describedby': 'producto-codigo-error',
    })
    assert.equal(propiedadesCampo(errores, 'producto-nombre')['aria-invalid'], undefined)
    let enfocado = false
    enfocarError({ elements: [{ id: 'producto-codigo', focus: () => { enfocado = true },
      scrollIntoView() {} }] }, errores)
    assert.equal(enfocado, true)
  })
}

test('los errores de relaciones y almacenamiento no invalidan Código', () => {
  for (const mensaje of ['La operacion entra en conflicto con relaciones existentes',
    'Los datos incumplen las restricciones de almacenamiento']) {
    assert.deepEqual(erroresPorCampo('producto', mensaje), {})
  }
})
