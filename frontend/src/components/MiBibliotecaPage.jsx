import MisPrestamosPage from "./MisPrestamosPage.jsx";
import MisReservasPage from "./MisReservasPage.jsx";

export default function MiBibliotecaPage() {
  return (
    <section aria-labelledby="mi-biblioteca-titulo">
      <h2 id="mi-biblioteca-titulo">Mi biblioteca</h2>
      <MisPrestamosPage />
      <MisReservasPage />
    </section>
  );
}
