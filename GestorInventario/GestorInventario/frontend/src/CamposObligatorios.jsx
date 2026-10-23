export function IndicadorObligatorio() {
  return <span className="indicador-obligatorio" aria-hidden="true">{'\u00a0'}*</span>
}

export function LeyendaObligatorios() {
  return <p className="leyenda-obligatorios">Los campos marcados con * son obligatorios.</p>
}
