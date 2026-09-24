import { Route, Routes } from 'react-router'
import CompanyPage from './pages/CompanyPage.jsx'
import RosterPage from './pages/RosterPage.jsx'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<RosterPage />} />
      <Route path="/companies/:id" element={<CompanyPage />} />
    </Routes>
  )
}
