import { Routes, Route, Navigate } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout'
import DashboardPage from './pages/DashboardPage'
import ValuationPage from './pages/ValuationPage'
import HedgeDesignationPage from './features/hedge/pages/HedgeDesignationPage'
import EffectivenessTestPage from './pages/EffectivenessTestPage'
import JournalEntryPage from './pages/JournalEntryPage'

export default function App() {
  return (
    <AppLayout>
      <Routes>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/hedge/designation" element={<HedgeDesignationPage />} />
        <Route path="/valuation" element={<ValuationPage />} />
        <Route path="/effectiveness" element={<EffectivenessTestPage />} />
        <Route path="/journal" element={<JournalEntryPage />} />
      </Routes>
    </AppLayout>
  )
}
