import { Route, Routes } from 'react-router'
import RosterPage from './pages/RosterPage.jsx'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<RosterPage />} />
    </Routes>
  )
}
