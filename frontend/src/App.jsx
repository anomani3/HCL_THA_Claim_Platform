import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Activity, ArrowDownRight, ArrowRight, ArrowUpRight, BadgeCheck, Bell, BriefcaseBusiness,
  CarFront, Check, ChevronDown, ChevronLeft, ChevronRight, CircleHelp, Clock3, FilePlus2,
  FileText, Filter, Globe2, LayoutDashboard, LifeBuoy, ListFilter, LoaderCircle, Menu,
  MoreHorizontal, Plus, Search, Shield, SlidersHorizontal, Sparkles, X, LogOut, LockKeyhole,
} from 'lucide-react'

const API = import.meta.env.VITE_API_BASE || ''
const STATUSES = ['SUBMITTED', 'UNDER_REVIEW', 'INFO_REQUESTED', 'ASSESSED', 'APPROVED', 'SETTLED', 'REJECTED', 'WITHDRAWN']
const MARKETS = ['SG', 'HK', 'AU', 'TH', 'MY', 'ID']
const blankClaim = { claimType: 'MOTOR', market: 'SG', currency: 'SGD', policyNumber: '', claimantId: '', claimantName: '', claimantEmail: '', incidentDate: '', incidentLocation: '', assetReference: '', description: '', claimedAmount: '' }

async function request(path, options = {}) {
  const token = localStorage.getItem('claimsToken')
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...options.headers },
  })
  if (!response.ok) {
    if (response.status === 401) window.dispatchEvent(new Event('auth-expired'))
    let message = `Request failed (${response.status})`
    try {
      const error = await response.json()
      message = error.message || error.detail || error.error || message
    } catch { /* API returned no JSON body */ }
    throw new Error(message)
  }
  if (response.status === 204) return null
  return response.json()
}

function money(value, currency = 'USD') {
  if (value == null || value === '') return '—'
  try { return new Intl.NumberFormat('en', { style: 'currency', currency, maximumFractionDigits: 0 }).format(value) }
  catch { return `${currency} ${Number(value).toLocaleString()}` }
}
function date(value) { return value ? new Date(value).toLocaleDateString('en', { day: 'numeric', month: 'short', year: 'numeric' }) : '—' }
function shortId(value = '') { return value.length > 18 ? `${value.slice(0, 8)}…${value.slice(-5)}` : value }
function statusClass(status = '') { return `status status-${status.toLowerCase()}` }
function initials(name = 'Claims team') { return name.split(/[\s-]+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase() }

function App() {
  const [session, setSession] = useState(null)
  const [checkingSession, setCheckingSession] = useState(true)

  useEffect(() => {
    const token = localStorage.getItem('claimsToken')
    if (!token) { setCheckingSession(false); return }
    request('/api/auth/me').then(setSession).catch(() => {
      localStorage.removeItem('claimsToken')
      setSession(null)
    }).finally(() => setCheckingSession(false))
  }, [])

  useEffect(() => {
    const expire = () => { localStorage.removeItem('claimsToken'); setSession(null) }
    window.addEventListener('auth-expired', expire)
    return () => window.removeEventListener('auth-expired', expire)
  }, [])

  const onAuthenticated = (auth) => {
    localStorage.setItem('claimsToken', auth.accessToken)
    setSession(auth.account)
  }
  const signOut = () => {
    localStorage.removeItem('claimsToken')
    setSession(null)
  }

  if (checkingSession) return <div className="auth-loading"><div className="brand-mark"><Shield size={20} /></div><LoaderCircle className="spin" size={18} /> Securing your workspace…</div>
  if (!session) return <AuthScreen onAuthenticated={onAuthenticated} />
  return <ClaimsWorkspace session={session} onSignOut={signOut} />
}

function ClaimsWorkspace({ session, onSignOut }) {
  const isManager = session.role === 'MANAGER'
  const isOfficer = session.role === 'OFFICER'
  const isClaimant = session.role === 'CLAIMANT'
  const [page, setPage] = useState(isManager ? 'Overview' : isOfficer ? 'My desk' : 'Claims')
  const [claims, setClaims] = useState([])
  const [pageInfo, setPageInfo] = useState({ page: 0, totalPages: 0, totalElements: 0 })
  const [reports, setReports] = useState({ workload: [], exposure: { rows: [], totalsByCurrency: {} }, performance: [], summary: [] })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState(null)
  const [history, setHistory] = useState([])
  const [drawerLoading, setDrawerLoading] = useState(false)
  const [query, setQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [marketFilter, setMarketFilter] = useState('')
  const [currentPage, setCurrentPage] = useState(0)
  const [showCreate, setShowCreate] = useState(false)
  const [createForm, setCreateForm] = useState(blankClaim)
  const [createBusy, setCreateBusy] = useState(false)
  const [trackNumber, setTrackNumber] = useState('')
  const [trackEmail, setTrackEmail] = useState('')
  const [trackBusy, setTrackBusy] = useState(false)
  const [trackResult, setTrackResult] = useState(null)
  const [trackError, setTrackError] = useState('')
  const [notice, setNotice] = useState('')
  const [actionBusy, setActionBusy] = useState(false)
  const [actionModal, setActionModal] = useState(null)
  const [mobileNav, setMobileNav] = useState(false)
  const [officerForm, setOfficerForm] = useState({ fullName: '', email: '', temporaryPassword: '' })
  const [officerBusy, setOfficerBusy] = useState(false)

  useEffect(() => {
    if (isClaimant) setCreateForm((form) => ({ ...form, claimantId: session.accountId, claimantName: session.fullName, claimantEmail: session.email }))
  }, [isClaimant, session.accountId, session.email, session.fullName])

  const loadReports = useCallback(async () => {
    const names = ['workload', 'exposure', 'performance', 'summary']
    const responses = await Promise.allSettled(names.map((name) => request(`/api/reports/${name}`)))
    const values = {}
    responses.forEach((result, index) => {
      if (result.status === 'fulfilled') values[names[index]] = result.value
      else values[names[index]] = names[index] === 'exposure' ? { rows: [], totalsByCurrency: {} } : []
    })
    setReports((previous) => ({ ...previous, ...values }))
    const failure = responses.find((result) => result.status === 'rejected')
    if (failure) setError((previous) => previous || `Reports are unavailable: ${failure.reason.message}`)
  }, [])

  const loadClaims = useCallback(async (pageNumber = currentPage) => {
    const params = new URLSearchParams({ page: String(pageNumber), size: '8', sort: 'createdAt,desc' })
    if (statusFilter) params.set('status', statusFilter)
    if (marketFilter) params.set('market', marketFilter)
    if (page === 'Work queue') { params.set('unassigned', 'true'); params.set('status', 'SUBMITTED') }
    if (page === 'My desk') { params.set('openOnly', 'true') }
    setLoading(true)
    try {
      const result = await request(`/api/claims?${params}`)
      setClaims(result.content || [])
      setPageInfo({ page: result.page || 0, totalPages: result.totalPages || 0, totalElements: result.totalElements || 0 })
      setError('')
    } catch (requestError) {
      setClaims([])
      setError(requestError.message)
    } finally { setLoading(false) }
  }, [currentPage, marketFilter, page, statusFilter])

  useEffect(() => { if (isManager) loadReports() }, [isManager, loadReports])
  useEffect(() => { loadClaims(currentPage) }, [loadClaims, currentPage])
  useEffect(() => {
    if (!notice) return undefined
    const timeout = setTimeout(() => setNotice(''), 3500)
    return () => clearTimeout(timeout)
  }, [notice])

  const allClaims = useMemo(() => claims.filter((claim) => {
    const search = query.trim().toLowerCase()
    return !search || [claim.claimNumber, claim.claimantName, claim.policyNumber, claim.market, claim.claimType]
      .some((value) => value?.toLowerCase().includes(search))
  }), [claims, query])
  const groupedSummary = useMemo(() => {
    const result = new Map()
    reports.summary.forEach((row) => result.set(row.status, (result.get(row.status) || 0) + row.count))
    return result
  }, [reports.summary])
  const openClaims = ['SUBMITTED', 'UNDER_REVIEW', 'INFO_REQUESTED', 'ASSESSED', 'APPROVED']
    .reduce((sum, key) => sum + (groupedSummary.get(key) || 0), 0)
  const settled = groupedSummary.get('SETTLED') || 0
  const totalClaims = reports.summary.reduce((sum, row) => sum + row.count, 0)
  const unassigned = reports.workload.find((entry) => entry.officerId === 'UNASSIGNED')
  const activeOfficers = reports.workload.filter((entry) => entry.officerId !== 'UNASSIGNED').length
  const exposureCurrencies = Object.entries(reports.exposure.totalsByCurrency || {})

  const selectClaim = async (claim) => {
    setSelected(claim)
    setHistory([])
    setDrawerLoading(true)
    try {
      const [, events] = await Promise.all([
        request(`/api/claims/${claim.id}`).then(setSelected),
        request(`/api/claims/${claim.id}/history`),
      ])
      setHistory(events || [])
    } catch (requestError) { setError(requestError.message) }
    finally { setDrawerLoading(false) }
  }

  const refresh = async () => { await Promise.all([loadClaims(currentPage), ...(isManager ? [loadReports()] : [])]) }
  const submitClaim = async (event) => {
    event.preventDefault()
    setCreateBusy(true)
    try {
      const created = await request('/api/claims', { method: 'POST', body: JSON.stringify({ ...createForm, claimedAmount: Number(createForm.claimedAmount) }) })
      setShowCreate(false)
      setCreateForm({ ...blankClaim, claimantId: session.accountId, claimantName: session.fullName, claimantEmail: session.email })
      setNotice(`Claim ${created.claimNumber} submitted successfully`)
      setPage('Claims')
      setCurrentPage(0)
      setSelected(created)
      refresh()
    } catch (requestError) { setError(requestError.message) }
    finally { setCreateBusy(false) }
  }
  const trackClaim = async (event) => {
    event.preventDefault(); setTrackBusy(true); setTrackError(''); setTrackResult(null)
    try { setTrackResult(await request(`/api/claims/track/${encodeURIComponent(trackNumber)}?email=${encodeURIComponent(trackEmail)}`)) }
    catch (requestError) { setTrackError(requestError.message) }
    finally { setTrackBusy(false) }
  }
  const runAction = async (event) => {
    event.preventDefault()
    if (!actionModal || !selected) return
    setActionBusy(true)
    const { action, note, amount } = actionModal
    const paths = { REQUEST_INFO: 'request-info', PROVIDE_INFO: 'provide-info', ASSESS: 'assess', ASSIGN: 'assign', APPROVE: 'approve', REJECT: 'reject', SETTLE: 'settle', WITHDRAW: 'withdraw' }
    try {
      const updated = await request(`/api/claims/${selected.id}/${paths[action]}`, {
        method: 'POST', body: JSON.stringify({ actorId: session.accountId, note, amount: amount === '' ? null : Number(amount) }),
      })
      setSelected(updated)
      setActionModal(null)
      setNotice(`${action.replaceAll('_', ' ').toLowerCase()} completed`)
      await Promise.all([loadClaims(currentPage), ...(isManager ? [loadReports()] : [])])
      const events = await request(`/api/claims/${updated.id}/history`)
      setHistory(events || [])
    } catch (requestError) { setError(requestError.message) }
    finally { setActionBusy(false) }
  }
  const pickNext = async () => {
    try {
      const claim = await request(`/api/claims/queue/pick${marketFilter ? `?market=${encodeURIComponent(marketFilter)}` : ''}`, { method: 'POST' })
      setNotice(`Claim ${claim.claimNumber} is now assigned to you`)
      await refresh(); selectClaim(claim)
    } catch (requestError) { setError(requestError.message) }
  }

  const createOfficer = async (event) => {
    event.preventDefault()
    setOfficerBusy(true)
    try {
      const officer = await request('/api/accounts/officers', { method: 'POST', body: JSON.stringify(officerForm) })
      setNotice(`Officer account created: ${officer.accountId}`)
      setOfficerForm({ fullName: '', email: '', temporaryPassword: '' })
    } catch (requestError) { setError(requestError.message) }
    finally { setOfficerBusy(false) }
  }

  const navItems = isManager
    ? [
      { group: 'WORKSPACE', links: [{ label: 'Overview', icon: LayoutDashboard }, { label: 'Claims', icon: FileText }, { label: 'Team accounts', icon: BriefcaseBusiness }] },
      { group: 'INSIGHTS', links: [{ label: 'Reports', icon: Activity }] },
    ]
    : isOfficer
      ? [{ group: 'WORKSPACE', links: [{ label: 'My desk', icon: BriefcaseBusiness }, { label: 'Work queue', icon: ListFilter }] }]
      : [{ group: 'MY ACCOUNT', links: [{ label: 'Claims', icon: FileText }, { label: 'Track a claim', icon: Search }] }]

  return <div className="app-shell">
    <aside className={`sidebar ${mobileNav ? 'sidebar-open' : ''}`}>
      <div className="brand"><div className="brand-mark"><Shield size={20} strokeWidth={2.4} /></div><div><strong>northstar</strong><span>CLAIMS PLATFORM</span></div><button className="icon-button mobile-close" onClick={() => setMobileNav(false)}><X size={18} /></button></div>
      <div className="workspace-switch"><div className="workspace-icon">A</div><div className="workspace-copy"><b>APAC Operations</b><small>{session.role.toLowerCase()} workspace</small></div><ChevronDown size={16} /></div>
      <nav className="primary-nav">{navItems.map((group) => <div className="nav-group" key={group.group}><p>{group.group}</p>{group.links.map(({ label, icon: Icon }) => <button key={label} className={`nav-link ${page === label ? 'active' : ''}`} onClick={() => { setPage(label); setCurrentPage(0); setMobileNav(false) }}><Icon size={18} strokeWidth={1.8} /><span>{label}</span>{label === 'Work queue' && unassigned?.openClaims > 0 && <i>{unassigned.openClaims}</i>}</button>)}</div>)}</nav>
      <div className="sidebar-bottom"><div className="help-card"><div className="help-icon"><CircleHelp size={17} /></div><div><b>Need a hand?</b><span>Visit the help center</span></div><ArrowRight size={15} /></div><div className="profile"><div className="avatar avatar-purple">{initials(session.fullName)}</div><div className="profile-copy"><b>{session.fullName}</b><span>{session.role.toLowerCase()} · {session.accountId}</span></div></div></div>
    </aside>
    {mobileNav && <button className="nav-scrim" aria-label="Close navigation" onClick={() => setMobileNav(false)} />}
    <main className="main-area">
      <header className="topbar"><button className="icon-button menu-button" onClick={() => setMobileNav(true)}><Menu size={20} /></button><div className="breadcrumbs"><span>Workspace</span><ChevronRight size={14} /><b>{page}</b></div><div className="topbar-right"><div className="global-search"><Search size={16} /><input placeholder="Search anything..." value={query} onChange={(event) => setQuery(event.target.value)} /><kbd>⌘ K</kbd></div><span className="role-pill">{session.role}</span><button className="button button-secondary logout-button" onClick={onSignOut}><LogOut size={14} /> Sign out</button></div></header>
      <div className="content-wrap">
        <div className="page-heading"><div><div className="eyebrow"><span className="live-dot" /> APAC REGION <span className="eyebrow-divider">·</span> {new Date().toLocaleDateString('en', { weekday: 'long', day: 'numeric', month: 'long' })}</div><h1>{page === 'Overview' ? `Good morning, ${session.fullName.split(' ')[0]}` : page}</h1><p>{page === 'Overview' ? 'Here’s what’s happening across your claims portfolio.' : page === 'Work queue' ? 'Review and pick up new claims waiting for an owner.' : page === 'My desk' ? 'Your open claims, all in one place.' : page === 'Reports' ? 'A closer look at workload, outcomes, and exposure.' : page === 'Team accounts' ? 'Create and manage access for your claims officers.' : page === 'Track a claim' ? 'Check the latest status and next step for your claim.' : 'Search, review, and manage every claim.'}</p></div><div className="heading-actions">{isOfficer && page === 'Work queue' && <button className="button button-secondary" onClick={pickNext}><Sparkles size={16} /> Pick next claim</button>}{isClaimant && <button className="button button-primary" onClick={() => setShowCreate(true)}><Plus size={17} /> New claim</button>}</div></div>

        {error && <div className="alert"><span>{error}</span><button onClick={() => setError('')}><X size={16} /></button></div>}
        {notice && <div className="toast"><Check size={16} />{notice}<button onClick={() => setNotice('')}><X size={15} /></button></div>}

        {page === 'Overview' && <>
          <section className="metric-grid">
            <MetricCard label="Total claims" value={totalClaims.toLocaleString()} icon={FileText} tint="violet" caption="Across all markets" trend="Portfolio" />
            <MetricCard label="Open claims" value={openClaims.toLocaleString()} icon={Clock3} tint="blue" caption="Awaiting resolution" trend={`${unassigned?.openClaims || 0} unassigned`} />
            <MetricCard label="Settled claims" value={settled.toLocaleString()} icon={BadgeCheck} tint="green" caption="Successfully resolved" trend="All time" />
            <MetricCard label="Active officers" value={activeOfficers.toString()} icon={BriefcaseBusiness} tint="orange" caption="With assigned work" trend="Team" />
          </section>
          <section className="overview-grid">
            <div className="card volume-card"><div className="card-head"><div><h2>Claims overview</h2><p>Volume across every market</p></div><span className="period-pill"><Globe2 size={14} /> All markets <ChevronDown size={14} /></span></div><div className="chart-legend"><span><i className="legend-dot dot-violet" /> Open <b>{openClaims}</b></span><span><i className="legend-dot dot-mint" /> Settled <b>{settled}</b></span><span className="legend-total">{totalClaims} <small>Total claims</small></span></div><div className="bar-chart">{MARKETS.map((market) => { const marketRows = reports.summary.filter((row) => row.market === market); const opened = marketRows.filter((row) => openClaimsStatuses.includes(row.status)).reduce((sum, row) => sum + row.count, 0); const closed = marketRows.filter((row) => ['SETTLED', 'REJECTED'].includes(row.status)).reduce((sum, row) => sum + row.count, 0); const max = Math.max(1, ...reports.summary.map((row) => row.count)); return <div className="bar-group" key={market}><div className="bars"><div className="bar bar-open" style={{ height: `${Math.max(opened ? 9 : 2, opened / max * 100)}%` }} title={`${opened} open`} /><div className="bar bar-settled" style={{ height: `${Math.max(closed ? 9 : 2, closed / max * 100)}%` }} title={`${closed} closed`} /></div><span>{market}</span></div> })}</div><div className="chart-foot"><span>Market distribution</span><button onClick={() => setPage('Reports')}>View detailed report <ArrowRight size={14} /></button></div></div>
            <div className="card distribution-card"><div className="card-head"><div><h2>Claim status</h2><p>Current portfolio breakdown</p></div><button className="icon-button small-dots"><MoreHorizontal size={19} /></button></div><div className="donut-wrap"><div className="donut" style={{ '--open-angle': `${totalClaims ? openClaims / totalClaims * 100 : 0}%`, '--settled-angle': `${totalClaims ? settled / totalClaims * 100 : 0}%` }}><div><strong>{totalClaims}</strong><span>Total claims</span></div></div></div><div className="status-legend"><div><i className="legend-dot dot-violet" /><span>In progress</span><b>{openClaims}</b></div><div><i className="legend-dot dot-mint" /><span>Settled</span><b>{settled}</b></div><div><i className="legend-dot dot-gray" /><span>Other</span><b>{Math.max(0, totalClaims - openClaims - settled)}</b></div></div></div>
          </section>
          <section className="card table-card"><div className="table-title"><div><h2>Recent claims</h2><p>The latest updates from your portfolio</p></div><button className="text-button" onClick={() => setPage('Claims')}>View all claims <ArrowRight size={15} /></button></div><ClaimTable claims={claims.slice(0, 5)} loading={loading} onSelect={selectClaim} /></section>
          <section className="lower-grid"><div className="card workload-card"><div className="card-head"><div><h2>Team workload</h2><p>Open claims by owner</p></div><button className="text-button" onClick={() => setPage('Work queue')}>View queue <ArrowRight size={15} /></button></div><WorkloadList workload={reports.workload} /></div><div className="card exposure-card"><div className="card-head"><div><h2>Outstanding exposure</h2><p>Liability by currency</p></div><div className="exposure-icon"><Activity size={17} /></div></div>{exposureCurrencies.length ? exposureCurrencies.slice(0, 3).map(([currency, amount]) => <div className="exposure-row" key={currency}><div className="currency-mark">{currency.slice(0, 1)}</div><div className="exposure-copy"><b>{currency}</b><span>Outstanding</span></div><strong>{money(amount, currency)}</strong></div>) : <EmptyInline loading={loading} text="No exposure to show yet" />}</div></section>
        </>}

        {page === 'Reports' && <><section className="metric-grid"><MetricCard label="Total claims" value={totalClaims.toLocaleString()} icon={FileText} tint="violet" caption="Across all markets" trend="Portfolio" /><MetricCard label="Open claims" value={openClaims.toLocaleString()} icon={Clock3} tint="blue" caption="Awaiting resolution" trend={`${unassigned?.openClaims || 0} unassigned`} /><MetricCard label="Settled claims" value={settled.toLocaleString()} icon={BadgeCheck} tint="green" caption="Successfully resolved" trend="All time" /><MetricCard label="Active officers" value={activeOfficers.toString()} icon={BriefcaseBusiness} tint="orange" caption="With assigned work" trend="Team" /></section><section className="reports-grid"><div className="card"><div className="card-head"><div><h2>Officer performance</h2><p>Throughput and resolution time</p></div></div><div className="table-scroll"><table><thead><tr><th>OFFICER</th><th>CURRENCY</th><th>SETTLED</th><th>REJECTED</th><th>OPEN</th><th>AVG. HOURS</th><th>TOTAL PAID</th></tr></thead><tbody>{reports.performance.map((row, index) => <tr key={`${row.officerId}-${row.currency}`}><td><div className="person-cell"><div className={`avatar avatar-${['purple', 'blue', 'peach'][index % 3]}`}>{initials(row.officerId)}</div><b>{row.officerId}</b></div></td><td>{row.currency}</td><td>{row.settled}</td><td>{row.rejected}</td><td>{row.open}</td><td>{row.avgResolutionHours ?? '—'}</td><td>{money(row.totalPaid, row.currency)}</td></tr>)}</tbody></table>{!reports.performance.length && <EmptyInline loading={loading} text="Performance data will appear here" />}</div></div><div className="card"><div className="card-head"><div><h2>Exposure by market</h2><p>Outstanding claim liability</p></div><Filter size={17} className="muted-icon" /></div><div className="table-scroll"><table><thead><tr><th>MARKET</th><th>TYPE</th><th>OPEN</th><th>EXPOSURE</th></tr></thead><tbody>{reports.exposure.rows.map((row, index) => <tr key={`${row.market}-${row.claimType}-${row.currency}-${index}`}><td><span className="market-badge">{row.market}</span></td><td>{row.claimType}</td><td>{row.openClaims}</td><td>{money(row.outstandingExposure, row.currency)}</td></tr>)}</tbody></table>{!reports.exposure.rows.length && <EmptyInline loading={loading} text="Exposure data will appear here" />}</div></div></section><section className="card table-card"><div className="table-title"><div><h2>Market status summary</h2><p>Claim volumes split by status</p></div></div><div className="status-summary-grid">{reports.summary.map((row) => <div className="status-summary-item" key={`${row.market}-${row.status}`}><span className="market-badge">{row.market}</span><span className={statusClass(row.status)}>{row.status.replaceAll('_', ' ')}</span><b>{row.count}</b></div>)}</div></section></>}

        {['Claims', 'Work queue', 'My desk'].includes(page) && <section className="card claims-page-card"><div className="list-toolbar"><div className="toolbar-copy"><h2>{page === 'Work queue' ? 'Unassigned claims' : page === 'My desk' ? 'Your active claims' : 'All claims'} <span className="count-chip">{pageInfo.totalElements}</span></h2><p>{page === 'Work queue' ? 'Oldest submissions are ready for assignment.' : 'A live view of claims in your portfolio.'}</p></div><div className="toolbar-filters">{page !== 'Work queue' && <label className="select-wrap"><SlidersHorizontal size={15} /><select value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value); setCurrentPage(0) }}><option value="">All statuses</option>{STATUSES.map((status) => <option key={status} value={status}>{status.replaceAll('_', ' ')}</option>)}</select><ChevronDown size={14} /></label>}<label className="select-wrap"><Globe2 size={15} /><select value={marketFilter} onChange={(event) => { setMarketFilter(event.target.value); setCurrentPage(0) }}><option value="">All markets</option>{MARKETS.map((market) => <option key={market}>{market}</option>)}</select><ChevronDown size={14} /></label><button className="button button-secondary filter-button" onClick={() => { setStatusFilter(''); setMarketFilter(''); setQuery(''); setCurrentPage(0) }}><ListFilter size={16} /> Reset</button></div></div><ClaimTable claims={allClaims} loading={loading} onSelect={selectClaim} /><div className="pagination"><span>Showing <b>{claims.length ? currentPage * 8 + 1 : 0}–{Math.min((currentPage + 1) * 8, pageInfo.totalElements)}</b> of <b>{pageInfo.totalElements}</b> claims</span><div><button disabled={currentPage <= 0} onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}><ChevronLeft size={16} /></button><span>Page {currentPage + 1} of {Math.max(1, pageInfo.totalPages)}</span><button disabled={currentPage + 1 >= pageInfo.totalPages} onClick={() => setCurrentPage((p) => p + 1)}><ChevronRight size={16} /></button></div></div></section>}

        {page === 'Track a claim' && <section className="track-layout"><div className="track-intro"><div className="track-illustration"><div className="track-orbit orbit-one" /><div className="track-orbit orbit-two" /><div className="track-shield"><Shield size={49} strokeWidth={1.6} /><Check size={19} /></div><div className="float-card float-card-one"><Clock3 size={15} /><span>Always in the loop</span></div><div className="float-card float-card-two"><Activity size={15} /><span>Live claim updates</span></div></div><span className="eyebrow">CLAIMANT PORTAL</span><h2>Your claim,<br />clearly tracked.</h2><p>Enter the claim number and email address used when you filed. We’ll show you the latest status and next steps.</p><div className="track-promise"><span><Shield size={15} /> Your details stay private</span><span><Clock3 size={15} /> Updated in real time</span></div></div><div className="track-form-card"><div className="form-icon"><Search size={19} /></div><h2>Track your claim</h2><p>We’ll find your claim in just a moment.</p><form onSubmit={trackClaim} className="stack-form"><Field label="Claim number"><input required placeholder="e.g. CLM-SG-2025-00142" value={trackNumber} onChange={(event) => setTrackNumber(event.target.value)} /></Field><Field label="Email address"><input required type="email" placeholder="you@example.com" value={trackEmail} onChange={(event) => setTrackEmail(event.target.value)} /></Field>{trackError && <p className="form-error">{trackError}</p>}<button className="button button-primary full-button" disabled={trackBusy}>{trackBusy ? <LoaderCircle size={17} className="spin" /> : null} Find my claim <ArrowRight size={16} /></button></form>{trackResult && <div className="track-result"><div className="track-result-top"><div><small>CLAIM NUMBER</small><strong>{trackResult.claimNumber}</strong></div><span className={statusClass(trackResult.status)}>{trackResult.status?.replaceAll('_', ' ')}</span></div><div className="track-result-grid"><div><small>Claim type</small><b>{trackResult.claimType}</b></div><div><small>Submitted</small><b>{date(trackResult.createdAt)}</b></div><div><small>Claim amount</small><b>{money(trackResult.claimedAmount, trackResult.currency)}</b></div><div><small>Last updated</small><b>{date(trackResult.updatedAt)}</b></div></div><button className="text-button" onClick={() => selectClaim(trackResult)}>View claim details <ArrowRight size={15} /></button></div>}</div></section>}

        {page === 'Team accounts' && <section className="team-account-layout"><div className="card team-intro-card"><div className="team-icon"><BriefcaseBusiness size={20} /></div><span className="eyebrow">ACCESS MANAGEMENT</span><h2>Build your claims team.</h2><p>Officer accounts can review the unassigned queue, own claims, and progress them through assessment and settlement.</p><div className="team-role-row"><span className="role-pill">OFFICER</span><span>Claims intake & lifecycle actions</span></div><div className="team-role-row"><span className="role-pill role-pill-manager">MANAGER</span><span>Portfolio reports & account provisioning</span></div></div><form className="card officer-form-card" onSubmit={createOfficer}><div className="card-head"><div><h2>Create officer account</h2><p>A temporary password lets them sign in right away.</p></div><div className="team-icon small"><Plus size={17} /></div></div><div className="stack-form"><Field label="Full name"><input required maxLength="255" value={officerForm.fullName} onChange={(event) => setOfficerForm({ ...officerForm, fullName: event.target.value })} placeholder="Officer name" /></Field><Field label="Work email"><input required type="email" value={officerForm.email} onChange={(event) => setOfficerForm({ ...officerForm, email: event.target.value })} placeholder="officer@company.com" /></Field><Field label="Temporary password (12+ characters)"><input required minLength="12" maxLength="72" type="password" value={officerForm.temporaryPassword} onChange={(event) => setOfficerForm({ ...officerForm, temporaryPassword: event.target.value })} placeholder="Create a temporary password" /></Field><button className="button button-primary full-button" disabled={officerBusy}>{officerBusy ? <LoaderCircle size={16} className="spin" /> : <Plus size={16} />} Create officer account</button></div></form></section>}
        {page === 'New claim' && isClaimant && <ClaimForm createForm={createForm} setCreateForm={setCreateForm} onSubmit={submitClaim} busy={createBusy} identityLocked />}
        <footer className="footer"><span>© 2025 Northstar Claims</span><div><span><i className="live-dot" /> All systems operational</span><a href="/swagger-ui.html" target="_blank" rel="noreferrer">API docs <ArrowRight size={12} /></a></div></footer>
      </div>
    </main>

    {selected && <><button className="drawer-scrim" aria-label="Close details" onClick={() => setSelected(null)} /><aside className="detail-drawer"><div className="drawer-head"><div><span className="eyebrow">CLAIM DETAILS</span><h2>{selected.claimNumber}</h2></div><button className="icon-button" onClick={() => setSelected(null)}><X size={19} /></button></div><div className="drawer-scroll"><div className="detail-status"><span className={statusClass(selected.status)}>{selected.status?.replaceAll('_', ' ')}</span><span className="detail-updated">Updated {date(selected.updatedAt)}</span></div><div className="detail-value-card"><span>Claimed amount</span><strong>{money(selected.claimedAmount, selected.currency)}</strong><small>Policy {selected.policyNumber}</small></div><div className="detail-section"><h3>Claimant</h3><div className="claimant-row"><div className="avatar avatar-blue">{initials(selected.claimantName)}</div><div><b>{selected.claimantName}</b><span>{selected.claimantEmail}</span></div></div></div><div className="detail-section detail-facts"><h3>Claim information</h3><Fact label="Claim type" value={selected.claimType} /><Fact label="Market" value={`${selected.market} · ${selected.currency}`} /><Fact label="Incident date" value={date(selected.incidentDate)} /><Fact label="Location" value={selected.incidentLocation || '—'} /><Fact label="Asset / vehicle" value={selected.assetReference || '—'} /><Fact label="Assigned to" value={selected.assignedOfficerId || 'Unassigned'} /></div><div className="detail-section description-section"><h3>Incident description</h3><p>{selected.description || 'No description provided.'}</p></div><div className="detail-section"><h3>Activity</h3>{drawerLoading ? <div className="inline-loading"><LoaderCircle className="spin" size={17} /> Loading history…</div> : history.length ? <div className="timeline">{history.map((entry, index) => <div className="timeline-item" key={`${entry.action}-${entry.occurredAt}-${index}`}><div className="timeline-marker"><Check size={11} /></div><div className="timeline-content"><div><b>{entry.action?.replaceAll('_', ' ')}</b><time>{date(entry.occurredAt)}</time></div><span>{entry.fromStatus || 'New'} → {entry.toStatus}</span>{entry.note && <p>{entry.note}</p>}<small>By {entry.actorId}</small></div></div>)}</div> : <p className="muted-copy">No activity recorded yet.</p>}</div></div><div className="drawer-actions">{(selected.allowedActions || []).filter((action) => isOfficer ? !['PROVIDE_INFO', 'WITHDRAW'].includes(action) : isClaimant ? ['PROVIDE_INFO', 'WITHDRAW'].includes(action) : false).map((action) => <button key={action} className={`button ${['APPROVE', 'SETTLE'].includes(action) ? 'button-primary' : 'button-secondary'}`} onClick={() => setActionModal({ action, actorId: session.accountId, note: '', amount: selected.assessedAmount || '' })}>{action.replaceAll('_', ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())}</button>)}{!selected.allowedActions?.some((action) => isOfficer ? !['PROVIDE_INFO', 'WITHDRAW'].includes(action) : isClaimant && ['PROVIDE_INFO', 'WITHDRAW'].includes(action)) && <span>No actions available</span>}</div></aside></>}

    {showCreate && isClaimant && <div className="modal-scrim" onMouseDown={(event) => { if (event.target === event.currentTarget) setShowCreate(false) }}><div className="modal create-modal"><div className="modal-head"><div><span className="eyebrow">CLAIM INTAKE</span><h2>Report an incident</h2><p>Share the details so our team can get started.</p></div><button className="icon-button" onClick={() => setShowCreate(false)}><X size={19} /></button></div><ClaimForm createForm={createForm} setCreateForm={setCreateForm} onSubmit={submitClaim} busy={createBusy} compact identityLocked /></div></div>}
    {actionModal && <div className="modal-scrim" onMouseDown={(event) => { if (event.target === event.currentTarget) setActionModal(null) }}><form className="modal action-modal" onSubmit={runAction}><div className="modal-head"><div><span className="eyebrow">CLAIM ACTION</span><h2>{actionModal.action.replaceAll('_', ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())}</h2><p>Claim {selected?.claimNumber}</p></div><button type="button" className="icon-button" onClick={() => setActionModal(null)}><X size={19} /></button></div><div className="stack-form">{actionModal.action === 'ASSESS' && <Field label="Assessed amount"><input required type="number" min="0.01" step="0.01" max={selected?.claimedAmount} value={actionModal.amount} onChange={(event) => setActionModal({ ...actionModal, amount: event.target.value })} placeholder="0.00" /></Field>}<Field label={`Note${['REQUEST_INFO', 'PROVIDE_INFO', 'REJECT'].includes(actionModal.action) ? ' (required)' : ' (optional)'}`}><textarea required={['REQUEST_INFO', 'PROVIDE_INFO', 'REJECT'].includes(actionModal.action)} rows="3" value={actionModal.note} onChange={(event) => setActionModal({ ...actionModal, note: event.target.value })} placeholder="Add a note for the claim record..." /></Field><button className="button button-primary full-button" disabled={actionBusy}>{actionBusy ? <LoaderCircle size={17} className="spin" /> : <Check size={16} />} Confirm action</button></div></form></div>}
  </div>
}

const openClaimsStatuses = ['SUBMITTED', 'UNDER_REVIEW', 'INFO_REQUESTED', 'ASSESSED', 'APPROVED']

function MetricCard({ label, value, icon: Icon, tint, caption, trend }) {
  return <div className="metric-card"><div className={`metric-icon tint-${tint}`}><Icon size={19} strokeWidth={1.9} /></div><div className="metric-top"><span>{label}</span><button className="metric-more" aria-label={`${label} information`}><MoreHorizontal size={17} /></button></div><strong>{value}</strong><div className="metric-bottom"><span>{caption}</span><span className="metric-trend"><span className="trend-mark"><ArrowUpRight size={12} /></span>{trend}</span></div></div>
}
function ClaimTable({ claims, loading, onSelect }) {
  if (loading && !claims.length) return <div className="table-loading"><LoaderCircle size={20} className="spin" /> Loading claims…</div>
  if (!claims.length) return <EmptyInline text="No claims match these filters" />
  return <div className="table-scroll"><table><thead><tr><th>CLAIM</th><th>CLAIMANT</th><th>TYPE</th><th>MARKET</th><th>AMOUNT</th><th>STATUS</th><th>UPDATED</th><th /></tr></thead><tbody>{claims.map((claim, index) => <tr key={claim.id || claim.claimNumber} className="claim-row" onClick={() => onSelect(claim)}><td><div className="claim-id"><div className={`claim-type-icon type-${claim.claimType?.toLowerCase()}`}><CarFront size={16} /></div><div><b>{claim.claimNumber || shortId(claim.id)}</b><span>#{String(index + 1).padStart(3, '0')} · {claim.policyNumber || 'Policy pending'}</span></div></div></td><td><div className="claimant-cell"><div className={`avatar avatar-${['blue', 'peach', 'mint', 'purple'][index % 4]}`}>{initials(claim.claimantName)}</div><span>{claim.claimantName || '—'}</span></div></td><td>{claim.claimType || '—'}</td><td><span className="market-badge">{claim.market || '—'}</span></td><td><b>{money(claim.claimedAmount, claim.currency)}</b></td><td><span className={statusClass(claim.status)}><i />{claim.status?.replaceAll('_', ' ')}</span></td><td>{date(claim.updatedAt)}</td><td><button className="row-arrow" aria-label={`View ${claim.claimNumber}`}><ArrowRight size={15} /></button></td></tr>)}</tbody></table></div>
}
function WorkloadList({ workload }) {
  if (!workload.length) return <EmptyInline text="Workload will appear as claims arrive" />
  const entries = [...workload].sort((a, b) => b.openClaims - a.openClaims).slice(0, 5)
  const maximum = Math.max(...entries.map((row) => row.openClaims), 1)
  return <div className="workload-list">{entries.map((row, index) => <div className="workload-row" key={row.officerId}><div className={`avatar avatar-${['purple', 'blue', 'peach', 'mint', 'gold'][index % 5]}`}>{initials(row.officerId)}</div><div className="workload-person"><b>{row.officerId === 'UNASSIGNED' ? 'Unassigned queue' : row.officerId}</b><span>{row.openClaims} open {row.openClaims === 1 ? 'claim' : 'claims'}</span></div><div className="workload-meter"><i style={{ width: `${Math.max(5, row.openClaims / maximum * 100)}%` }} /></div><strong>{row.openClaims}</strong></div>)}</div>
}
function Field({ label, children, className = '' }) { return <label className={`field ${className}`}><span>{label}</span>{children}</label> }
function Fact({ label, value }) { return <div className="fact-row"><span>{label}</span><b>{value}</b></div> }
function EmptyInline({ text, loading = false }) { return <div className="empty-inline">{loading ? <LoaderCircle size={18} className="spin" /> : <FileText size={18} />}<span>{loading ? 'Loading your data…' : text}</span></div> }

function ClaimForm({ createForm, setCreateForm, onSubmit, busy, onCancel, compact = false, identityLocked = false }) {
  const update = (field) => (event) => setCreateForm({ ...createForm, [field]: event.target.value })
  return <form onSubmit={onSubmit} className={`claim-form ${compact ? 'claim-form-compact' : ''}`}><div className="form-section"><div className="form-section-title"><span>01</span><div><b>Incident details</b><small>Tell us what happened.</small></div></div><div className="form-grid"><Field label="Claim type"><select value={createForm.claimType} onChange={update('claimType')}><option value="MOTOR">Motor</option><option value="PROPERTY">Property</option></select></Field><Field label="Market"><select value={createForm.market} onChange={update('market')}>{MARKETS.map((market) => <option key={market}>{market}</option>)}</select></Field><Field label="Incident date"><input type="date" required value={createForm.incidentDate} onChange={update('incidentDate')} /></Field><Field label="Location"><input value={createForm.incidentLocation} onChange={update('incidentLocation')} placeholder="City, country" /></Field><Field label="Vehicle registration / property address" className="field-full"><input value={createForm.assetReference} onChange={update('assetReference')} placeholder="Asset or property details" /></Field><Field label="What happened?" className="field-full"><textarea required minLength="1" maxLength="4000" rows="3" value={createForm.description} onChange={update('description')} placeholder="Describe the incident and any damage..." /></Field></div></div><div className="form-section"><div className="form-section-title"><span>02</span><div><b>Policy & claimant</b><small>{identityLocked ? 'Your account details are attached automatically.' : 'We’ll use these details to keep you updated.'}</small></div></div><div className="form-grid"><Field label="Policy number"><input required value={createForm.policyNumber} onChange={update('policyNumber')} placeholder="e.g. POL-MTR-889012" /></Field><Field label="Claimant ID"><input required readOnly={identityLocked} value={createForm.claimantId} onChange={update('claimantId')} placeholder="e.g. CLMT-1001" /></Field><Field label="Full name"><input required readOnly={identityLocked} value={createForm.claimantName} onChange={update('claimantName')} placeholder="Your name" /></Field><Field label="Email address"><input required readOnly={identityLocked} type="email" value={createForm.claimantEmail} onChange={update('claimantEmail')} placeholder="you@example.com" /></Field><Field label="Currency"><select value={createForm.currency} onChange={update('currency')}>{['SGD', 'HKD', 'AUD', 'THB', 'MYR', 'IDR'].map((currency) => <option key={currency}>{currency}</option>)}</select></Field><Field label="Claimed amount"><input required type="number" min="0.01" step="0.01" value={createForm.claimedAmount} onChange={update('claimedAmount')} placeholder="0.00" /></Field></div></div><div className="form-actions">{onCancel && <button type="button" className="button button-secondary" onClick={onCancel}>Cancel</button>}<button className="button button-primary" disabled={busy}>{busy ? <LoaderCircle size={17} className="spin" /> : <FilePlus2 size={16} />} Submit claim <ArrowRight size={16} /></button></div></form>
}

function AuthScreen({ onAuthenticated }) {
  const [mode, setMode] = useState('login')
  const [form, setForm] = useState({ fullName: '', email: '', password: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const submit = async (event) => {
    event.preventDefault()
    setBusy(true); setError('')
    try {
      const path = mode === 'register' ? '/api/auth/register' : '/api/auth/login'
      const payload = mode === 'register' ? form : { email: form.email, password: form.password }
      onAuthenticated(await request(path, { method: 'POST', body: JSON.stringify(payload) }))
    } catch (requestError) { setError(requestError.message) }
    finally { setBusy(false) }
  }

  return <main className="auth-shell"><div className="auth-art"><div className="auth-art-top"><div className="brand-mark"><Shield size={20} /></div><div><strong>northstar</strong><span>CLAIMS PLATFORM</span></div></div><div className="auth-orbit auth-orbit-a" /><div className="auth-orbit auth-orbit-b" /><div className="auth-shield"><Shield size={80} strokeWidth={1.2} /><Check size={25} /></div><div className="auth-float auth-float-one"><BadgeCheck size={16} /><span>Every claim, accounted for.</span></div><div className="auth-float auth-float-two"><LockKeyhole size={15} /><span>Secure role-based access</span></div><div className="auth-art-copy"><span>CLAIMS, WITH CLARITY</span><h1>Care starts with<br />being in control.</h1><p>A clear path through every claim, from first notice to final resolution.</p></div><div className="auth-art-foot"><span><i className="live-dot" /> Secure workspace</span><span>Built for APAC operations</span></div></div><section className="auth-panel"><div className="auth-mobile-brand"><div className="brand-mark"><Shield size={20} /></div><strong>northstar</strong></div><div className="auth-card"><div className="auth-welcome"><span className="eyebrow">WELCOME TO NORTHSTAR</span><h2>{mode === 'login' ? 'Sign in to your workspace' : 'Create your claimant account'}</h2><p>{mode === 'login' ? 'Use your account email and password to continue.' : 'Set up an account to submit and manage your claims.'}</p></div><div className="auth-tabs"><button className={mode === 'login' ? 'selected' : ''} onClick={() => { setMode('login'); setError('') }}>Sign in</button><button className={mode === 'register' ? 'selected' : ''} onClick={() => { setMode('register'); setError('') }}>Create account</button></div><form className="stack-form auth-form" onSubmit={submit}>{mode === 'register' && <Field label="Full name"><input required maxLength="255" autoComplete="name" value={form.fullName} onChange={(event) => setForm({ ...form, fullName: event.target.value })} placeholder="Your full name" /></Field>}<Field label="Email address"><input required type="email" autoComplete="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} placeholder="you@example.com" /></Field><Field label="Password"><input required minLength={mode === 'register' ? 12 : undefined} maxLength="72" type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} placeholder={mode === 'register' ? 'At least 12 characters' : 'Enter your password'} /></Field>{error && <div className="auth-error">{error}</div>}<button className="button button-primary full-button" disabled={busy}>{busy ? <LoaderCircle size={16} className="spin" /> : <LockKeyhole size={15} />}{mode === 'login' ? 'Sign in securely' : 'Create claimant account'}<ArrowRight size={15} /></button></form><div className="auth-role-note"><Shield size={15} /><p>Role permissions are assigned to your account. Claimant signup is open; officer accounts are created by your manager.</p></div></div><div className="auth-panel-foot">© 2025 Northstar Claims <span>·</span> <a href="/swagger-ui.html" target="_blank" rel="noreferrer">API status</a></div></section></main>
}

export default App
