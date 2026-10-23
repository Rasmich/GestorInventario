import { createElement, useCallback, useEffect, useRef, useState } from 'react'
import { enfocarError, erroresPorCampo, propiedadesCampo } from './erroresFormulario'

export function useErroresFormulario(nombre, ocupado = false) {
  const [fallo, setFallo] = useState({ mensaje: '', campos: {} })
  const formulario = useRef(null)
  const aviso = useRef(null)
  const setError = useCallback((error, identificadores = []) => {
    const mensaje = typeof error === 'string' ? error : error.message
    const identificados = identificadores.length
      ? Object.fromEntries(identificadores.map((id) => [id, mensaje]))
      : erroresPorCampo(nombre, mensaje, error?.campos)
    // A missing control (for example in another user form mode) cannot describe an error.
    const disponibles = new Set(Array.from(formulario.current?.elements ?? []).map((campo) => campo.id))
    const campos = Object.fromEntries(Object.entries(identificados).filter(([id]) => disponibles.has(id)))
    setFallo({ mensaje, campos, enfocar: true })
  }, [nombre])

  useEffect(() => {
    if (fallo.mensaje && fallo.enfocar && !ocupado) enfocarError(formulario.current, fallo.campos, aviso.current)
  }, [fallo, ocupado])

  function onInvalid(evento) {
    // Replace the browser popup with the same messages next to the fields.
    evento.preventDefault()
    const invalidos = Array.from(evento.currentTarget.elements)
      .filter((campo) => campo.id && campo.willValidate && !campo.validity.valid)
    setFallo({ mensaje: invalidos.map((campo) => campo.validationMessage).join(' · '),
      campos: Object.fromEntries(invalidos.map((campo) => [campo.id, campo.validationMessage])), enfocar: true })
  }

  function onChange(evento) {
    const id = evento.target.id
    setFallo((anterior) => {
      if (!anterior.campos[id]) return anterior
      const restantes = { ...anterior.campos }
      delete restantes[id]
      // Leave other fields marked; editing must not move focus again.
      return { ...anterior, campos: restantes, enfocar: false,
        mensaje: Object.keys(restantes).length ? anterior.mensaje : '' }
    })
  }

  return {
    error: fallo.mensaje,
    setError,
    formProps: { ref: formulario, onInvalid, onChange },
    avisoProps: { ref: aviso, role: Object.keys(fallo.campos).length ? undefined : 'alert',
      hidden: Object.keys(fallo.campos).length > 0, className: 'error error-formulario' },
    campoProps: (id, ayuda) => propiedadesCampo(fallo.campos, id, ayuda),
    errorCampo: (id) => fallo.campos[id] ? createElement('p', {
      id: `${id}-error`, className: 'error error-formulario',
    }, fallo.campos[id]) : null,
  }
}
