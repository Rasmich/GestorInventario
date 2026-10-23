const trazos = {
  inicio: 'M3 10 12 3l9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z',
  productos: 'm12 3 9 5-9 5-9-5 9-5Zm-9 5v10l9 5 9-5V8M12 13v10M7.5 5.5l9 5',
  categorias: 'M3 3h7v7H3V3Zm11 0h7v7h-7V3ZM3 14h7v7H3v-7Zm11 0h7v7h-7v-7Z',
  proveedores: 'M3 5h12v12H3V5Zm12 5h4l3 4v3h-7M5 17a2 2 0 1 0 4 0m7 0a2 2 0 1 0 4 0',
  movimientos: 'M4 7h16m-4-4 4 4-4 4M20 17H4m4-4-4 4 4 4',
  usuarios: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M16 4a4 4 0 0 1 0 8m6 9v-2a4 4 0 0 0-3-3.87M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  salir: 'M9 21H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5m7 14 5-5-5-5M21 12H9',
  actualizar: 'M20 7v5h-5M4 17v-5h5M6 6a8 8 0 0 1 13 3M5 15a8 8 0 0 0 13 3',
  alerta: 'm12 3 10 18H2L12 3Zm0 6v4m0 4v.01',
  stock: 'M4 20V10m5 10V4m6 16v-7m5 7V7',
  actividad: 'M3 12h4l3-8 4 16 3-8h4',
}

export default function Icono({ nombre, className = '' }) {
  return <svg className={`icono ${className}`} viewBox="0 0 24 24" fill="none"
    stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"
    aria-hidden="true" focusable="false">
    <path d={trazos[nombre] ?? trazos.productos} />
  </svg>
}
