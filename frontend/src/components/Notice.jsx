// A quiet notice for a screen's loading, empty, and not-found states: nothing is wrong, there is
// just nothing to show (yet).

/** A muted, centred paragraph. */
export default function Notice({ children }) {
  return <p className="mt-6 rounded bg-gray-50 px-3 py-6 text-center text-gray-600">{children}</p>
}
