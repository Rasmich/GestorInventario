import { useErroresFormulario } from './useErroresFormulario'
import { IndicadorObligatorio, LeyendaObligatorios } from './CamposObligatorios'
import { fetchApi, leerRespuesta } from './api'

import { useEffect, useRef, useState } from 'react'


const formularioInicial = {
    codigo: '',
    nombre: '',
    descripcion: '',
    precioCompra: '',
    precioVenta: '',
    stockMinimo: '0',
    categoriaId: '',
    proveedorId: '',
}

function FormularioProducto({ token, productoInicial, alGuardar, alCancelar }) {
    const [formulario, setFormulario] = useState(() => productoInicial ? {
        codigo: productoInicial.codigo,
        nombre: productoInicial.nombre,
        descripcion: productoInicial.descripcion ?? '',
        precioCompra: String(productoInicial.precioCompra),
        precioVenta: String(productoInicial.precioVenta),
        stockMinimo: String(productoInicial.stockMinimo),
        categoriaId: String(productoInicial.categoria.id),
        proveedorId: String(productoInicial.proveedor.id),
    } : formularioInicial)
    const enviando = useRef(false)
    const activo = useRef(false)
    useEffect(() => {
        activo.current = true
        return () => { activo.current = false }
    }, [])
    const [categorias, setCategorias] = useState([])
    const [proveedores, setProveedores] = useState([])
    const [cargando, setCargando] = useState(true)
    const [guardando, setGuardando] = useState(false)
    const { error, setError, formProps, avisoProps, campoProps, errorCampo } = useErroresFormulario('producto', guardando)

    useEffect(() => {
        const controlador = new AbortController()

        async function cargarDatos() {
            try {
                const opciones = {
                    headers: { Authorization: `Bearer ${token}` },
                    signal: controlador.signal,
                }

                const [respuestaCategorias, respuestaProveedores] =
                    await Promise.all([
                        fetchApi('/api/categorias', opciones),
                        fetchApi('/api/proveedores', opciones),
                    ])

                const [datosCategorias, datosProveedores] = await Promise.all([
                    leerRespuesta(respuestaCategorias), leerRespuesta(respuestaProveedores),
                ])

                setCategorias(datosCategorias)
                setProveedores(datosProveedores)
            } catch (error) {
                if (error.name !== 'AbortError') {
                    setError(error.message)
                }
            } finally {
                if (!controlador.signal.aborted) setCargando(false)
            }
        }

        cargarDatos()
        return () => controlador.abort()
    }, [token, setError])

    function cambiarCampo(evento) {
        const { name, value } = evento.target

        setFormulario((anterior) => ({
            ...anterior,
            [name]: value,
        }))
    }

    async function guardarProducto(evento) {
        evento.preventDefault()
        if (enviando.current) return
        setError('')
        if (!formulario.codigo.trim() || !formulario.nombre.trim()) {
            setError('El código y el nombre no pueden contener solo espacios.',
                ['codigo', 'nombre'].filter((campo) => !formulario[campo].trim()).map((campo) => `producto-${campo}`))
            return
        }
        enviando.current = true
        setGuardando(true)

        const producto = {
            codigo: formulario.codigo.trim(),
            nombre: formulario.nombre.trim(),
            descripcion: formulario.descripcion,
            precioCompra: Number(formulario.precioCompra),
            precioVenta: Number(formulario.precioVenta),
            stockMinimo: Number(formulario.stockMinimo),
            categoriaId: Number(formulario.categoriaId),
            proveedorId: Number(formulario.proveedorId),
        }

        try {
            const respuesta = await fetchApi(productoInicial ? `/api/productos/${productoInicial.id}` : '/api/productos', {
                method: productoInicial ? 'PUT' : 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    Authorization: `Bearer ${token}`,
                },
                body: JSON.stringify(producto),
            })

            const productoCreado = await leerRespuesta(respuesta, `No se ha podido guardar el producto (${respuesta.status}).`)
            if (activo.current) alGuardar(productoCreado)
        } catch (error) {
            if (activo.current) setError(error)
        } finally {
            enviando.current = false
            if (activo.current) setGuardando(false)
        }
    }

    if (cargando) return (
        <div className="formulario-producto" aria-live="polite">
            <p>Cargando categorías y proveedores...</p>
            <button type="button" onClick={alCancelar}>Cancelar</button>
        </div>
    )

    return (
        <form {...formProps} className="formulario-producto" onSubmit={guardarProducto}>
            <h3>{productoInicial ? 'Editar producto' : 'Nuevo producto'}</h3>
            <p>El stock se modifica exclusivamente desde Movimientos.</p>
            {productoInicial && <p>Stock actual: {productoInicial.stock} · {productoInicial.activo ? 'Activo' : 'Inactivo'}. La edición conserva este estado.</p>}

            {error && <p {...avisoProps}>{error}</p>}
            {!error && (categorias.length === 0 || proveedores.length === 0) && (
                <p role="status">Necesitas al menos una categoría y un proveedor para crear un producto.</p>
            )}

            <fieldset disabled={guardando}>
            <legend>Datos del producto</legend>

            <LeyendaObligatorios />
            <label htmlFor="producto-codigo">Código<IndicadorObligatorio /></label>
            <input {...campoProps('producto-codigo')} id="producto-codigo"
                name="codigo"
                maxLength={255}
                value={formulario.codigo}
                onChange={cambiarCampo}
                required
            />
            {errorCampo('producto-codigo')}

            <label htmlFor="producto-nombre">Nombre<IndicadorObligatorio /></label>
            <input {...campoProps('producto-nombre')} id="producto-nombre"
                name="nombre"
                maxLength={255}
                value={formulario.nombre}
                onChange={cambiarCampo}
                required
            />
            {errorCampo('producto-nombre')}

            <label htmlFor="producto-descripcion">Descripción (opcional)</label>
            <textarea {...campoProps('producto-descripcion')} id="producto-descripcion"
                name="descripcion"
                maxLength={255}
                value={formulario.descripcion}
                onChange={cambiarCampo}
            />
            {errorCampo('producto-descripcion')}

            <label htmlFor="producto-precioCompra">Precio de compra (€)<IndicadorObligatorio /></label>
            <input {...campoProps('producto-precioCompra')} id="producto-precioCompra"
                max="99999999.99"
                type="number"
                name="precioCompra"
                min="0"
                step="0.01"
                value={formulario.precioCompra}
                onChange={cambiarCampo}
                required
            />
            {errorCampo('producto-precioCompra')}

            <label htmlFor="producto-precioVenta">Precio de venta (€)<IndicadorObligatorio /></label>
            <input {...campoProps('producto-precioVenta')} id="producto-precioVenta"
                max="99999999.99"
                type="number"
                name="precioVenta"
                min="0"
                step="0.01"
                value={formulario.precioVenta}
                onChange={cambiarCampo}
                required
            />
            {errorCampo('producto-precioVenta')}

            <label htmlFor="producto-stockMinimo">Stock mínimo<IndicadorObligatorio /></label>
            <input {...campoProps('producto-stockMinimo')} id="producto-stockMinimo"
                max="2147483647"
                type="number"
                name="stockMinimo"
                min="0"
                step="1"
                value={formulario.stockMinimo}
                onChange={cambiarCampo}
                required
            />
            {errorCampo('producto-stockMinimo')}

            <label htmlFor="producto-categoriaId">Categoría<IndicadorObligatorio /></label>
            <select {...campoProps('producto-categoriaId')} id="producto-categoriaId"
                name="categoriaId"
                value={formulario.categoriaId}
                onChange={cambiarCampo}
                required
            >
                <option value="">Selecciona una categoría</option>
                {categorias.map((categoria) => (
                    <option key={categoria.id} value={categoria.id}>
                        {categoria.nombre}
                    </option>
                ))}
            </select>
            {errorCampo('producto-categoriaId')}

            <label htmlFor="producto-proveedorId">Proveedor<IndicadorObligatorio /></label>
            <select {...campoProps('producto-proveedorId')} id="producto-proveedorId"
                name="proveedorId"
                value={formulario.proveedorId}
                onChange={cambiarCampo}
                required
            >
                <option value="">Selecciona un proveedor</option>
                {proveedores.map((proveedor) => (
                    <option key={proveedor.id} value={proveedor.id}>
                        {proveedor.nombre}
                    </option>
                ))}
            </select>
            {errorCampo('producto-proveedorId')}
            </fieldset>

            <div className="botones-formulario">
                <button
                    type="submit"
                    disabled={
                        guardando ||
                        categorias.length === 0 ||
                        proveedores.length === 0
                    }
                >
                    {guardando ? 'Guardando...' : 'Guardar producto'}
                </button>

                <button type="button" onClick={alCancelar} disabled={guardando}>
                    Cancelar
                </button>
            </div>
        </form>
    )
}

export default FormularioProducto
