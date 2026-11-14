import React, { useState, useEffect } from 'react';
import { AuthProvider, useAuth, ROLE_PERMISSIONS, DEFAULT_TAB_FOR_ROLE } from './context/AuthContext';
import { ThemeProvider } from './context/ThemeContext';
import Navbar from './components/Navbar';
import Sidebar from './components/Sidebar';
import FloorPlan from './pages/FloorPlan';
import AlertCenter from './pages/AlertCenter';
import KitchenKds from './pages/KitchenKds';
import WaiterDashboard from './pages/WaiterDashboard';
import CleaningTasks from './pages/CleaningTasks';
import CamerasZones from './pages/CamerasZones';
import Analytics from './pages/Analytics';
import CustomerQrPortal from './pages/CustomerQrPortal';
import Login from './pages/Login';
import { ShieldAlert } from 'lucide-react';

function MainLayout() {
  const { user, loading } = useAuth();
  const userRole = user?.jobRole || 'WAITER';
  const allowedTabs = ROLE_PERMISSIONS[userRole] || ['floor'];
  const defaultTab = DEFAULT_TAB_FOR_ROLE[userRole] || 'floor';

  const [activeTab, setActiveTab] = useState(defaultTab);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  useEffect(() => {
    if (user) {
      setActiveTab(defaultTab);
    }
  }, [user?.jobRole, defaultTab]);

  if (loading) {
    return (
      <div
        className="min-h-screen flex items-center justify-center"
        style={{ backgroundColor: 'var(--bg-main)', color: 'var(--text-primary)' }}
      >
        <div className="flex flex-col items-center gap-3">
          <div
            className="w-8 h-8 border-2 border-t-transparent rounded-full motion-safe:animate-spin motion-reduce:animate-none"
            style={{ borderColor: 'var(--text-primary)', borderTopColor: 'transparent' }}
          ></div>
          <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
            Initializing RESTROVISION…
          </span>
        </div>
      </div>
    );
  }

  if (!user) {
    return <Login />;
  }

  const handleSelectTab = (tab) => {
    setActiveTab(tab);
    setMobileMenuOpen(false);
  };

  const renderContent = () => {
    if (!allowedTabs.includes(activeTab)) {
      return (
        <div
          className="p-8 max-w-md mx-auto my-12 rounded-2xl border text-center space-y-4"
          style={{ backgroundColor: 'var(--bg-card)', borderColor: 'var(--border)' }}
        >
          <div
            className="w-12 h-12 rounded-xl mx-auto flex items-center justify-center border"
            style={{
              backgroundColor: 'var(--status-critical-bg)',
              borderColor: 'var(--status-critical-border)',
              color: 'var(--status-critical)',
            }}
          >
            <ShieldAlert className="w-6 h-6" />
          </div>
          <div>
            <h2 className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>
              Access Restricted
            </h2>
            <p className="text-xs mt-1" style={{ color: 'var(--text-muted)' }}>
              This operational view requires senior clearance. Your role ({userRole}) does not have permission to view senior operations data.
            </p>
          </div>
          <button
            onClick={() => setActiveTab(defaultTab)}
            className="px-4 py-2 rounded-xl text-xs font-bold transition-opacity whitespace-nowrap"
            style={{ backgroundColor: 'var(--btn-primary)', color: 'var(--btn-primary-text)' }}
          >
            Return to Dashboard
          </button>
        </div>
      );
    }

    switch (activeTab) {
      case 'floor':
        return <FloorPlan onSelectTab={handleSelectTab} />;
      case 'alerts':
        return <AlertCenter />;
      case 'kitchen':
        return <KitchenKds />;
      case 'waiter':
        return <WaiterDashboard />;
      case 'cleaning':
        return <CleaningTasks />;
      case 'cctv':
        return <CamerasZones />;
      case 'analytics':
        return <Analytics />;
      case 'qr':
        return <CustomerQrPortal />;
      default:
        return <FloorPlan onSelectTab={handleSelectTab} />;
    }
  };

  return (
    <div
      className="min-h-screen flex flex-col font-sans transition-colors duration-150"
      style={{
        backgroundColor: 'var(--bg-main)',
        color: 'var(--text-primary)',
      }}
    >
      <Navbar
        onSelectTab={handleSelectTab}
        mobileMenuOpen={mobileMenuOpen}
        onToggleMobileMenu={() => setMobileMenuOpen((prev) => !prev)}
      />
      <div className="flex flex-1 overflow-hidden relative">
        <Sidebar
          activeTab={activeTab}
          onSelectTab={handleSelectTab}
          mobileOpen={mobileMenuOpen}
          onCloseMobile={() => setMobileMenuOpen(false)}
        />
        <main
          className="flex-1 p-4 md:p-6 overflow-y-auto h-[calc(100vh-61px)] transition-colors duration-150 w-full"
          style={{ backgroundColor: 'var(--bg-main)' }}
        >
          {renderContent()}
        </main>
      </div>
    </div>
  );
}

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <MainLayout />
      </AuthProvider>
    </ThemeProvider>
  );
}
