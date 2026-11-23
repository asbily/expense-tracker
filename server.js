import express from 'express';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = Number(process.env.PORT) || 3000;
const HOST = '0.0.0.0';

app.use(express.static(__dirname, { index: 'index.html' }));

const EXPENSE_TRACKER_PAGES = {
  '/expense-tracker/': {
    title: 'Asbily Expense Tracker — Expense by Asbily',
    heading: 'Expense',
    subheading: 'Asbily Expense Tracker · Version 1.0.0',
    canonical: '/expense-tracker/',
    sections: [
      {
        title: 'Product Overview',
        body: 'Expense by Asbily (Package Name: com.asbily.expensetracker) is a fast, offline-first personal finance and expense ledger crafted by Asbily Platforms. Track daily cash flow, multi-wallet balances, category budgets, recurring schedules, and local encrypted backups right on your device.'
      },
      {
        title: 'Application Metadata',
        list: [
          'Full App Name: Asbily Expense Tracker',
          'App Name: Expense',
          'Developer: Asbily (Asbily Platforms)',
          'Package Name: com.asbily.expensetracker',
          'Version: 1.0.0'
        ]
      },
      {
        title: 'Support & Contact',
        body: 'For product inquiries, bug reports, or customer assistance, reach Asbily Platforms at support@asbily.com.'
      }
    ]
  },
  '/expense-tracker/privacy': {
    title: 'Privacy Policy — Asbily Expense Tracker',
    heading: 'Privacy Policy',
    subheading: 'Asbily Expense Tracker · Effective Version 1.0.0',
    canonical: '/expense-tracker/privacy',
    sections: [
      {
        title: '1. Local-First Data Ownership',
        body: 'Asbily Expense Tracker stores all transactions, wallets, categories, budgets, recurring rules, and optional 4-digit PIN security hashes strictly on your device using local browser/app storage. Asbily Platforms does not upload, sell, or transmit your financial ledger to external servers.'
      },
      {
        title: '2. Permissions & Offline Access',
        body: 'The application uses local Service Worker caching solely so the interface, icons, and your offline financial ledger launch reliably without an internet connection.'
      },
      {
        title: '3. Backups & Data Deletion',
        body: 'You can export CSV, JSON backups, or PDF financial summaries at any time from Settings. Deleted items move to Deleted Records first and can be permanently erased individually or via Wipe All Local Data.'
      },
      {
        title: '4. Privacy Contact',
        body: 'Questions about this Privacy Policy may be sent to Asbily Platforms at support@asbily.com.'
      }
    ]
  },
  '/expense-tracker/terms': {
    title: 'Terms of Service — Asbily Expense Tracker',
    heading: 'Terms of Service',
    subheading: 'Asbily Expense Tracker · Version 1.0.0',
    canonical: '/expense-tracker/terms',
    sections: [
      {
        title: '1. Acceptance of Terms',
        body: 'By accessing or using Asbily Expense Tracker ("Expense", package com.asbily.expensetracker) provided by Asbily Platforms, you agree to these Terms of Service.'
      },
      {
        title: '2. Personal Finance Tooling',
        body: 'Asbily Expense Tracker is provided as a personal bookkeeping and budgeting utility. It does not constitute regulated financial, tax, investment, or legal advice.'
      },
      {
        title: '3. User Responsibility & Backups',
        body: 'Because records reside locally on your device, you are responsible for safeguarding your device, remembering your optional PIN/security answer, and maintaining periodic JSON backups.'
      },
      {
        title: '4. Limitation of Liability',
        body: 'To the fullest extent permitted by applicable law, Asbily Platforms provides the application "as is" without warranties of any kind and is not liable for local device data loss.'
      }
    ]
  },
  '/expense-tracker/licenses': {
    title: 'Asbily Licenses — Asbily Expense Tracker',
    heading: 'Asbily Licenses',
    subheading: 'Asbily Platforms Proprietary & Brand License',
    canonical: '/expense-tracker/licenses',
    sections: [
      {
        title: '1. Brand & Trademarks',
        body: 'The "Asbily", "Asbily Platforms", and "Asbily Expense Tracker" names, official logo artwork (/asbily-logo.jpg), and visual identity remain the property of Asbily Platforms.'
      },
      {
        title: '2. Application Usage License',
        body: 'Asbily Platforms grants you a personal, non-exclusive, revocable license to use Asbily Expense Tracker (com.asbily.expensetracker v1.0.0) for personal and small-ledger expense tracking.'
      },
      {
        title: '3. Commercial Redistribution',
        body: 'Unauthorized rebranding, resale, or redistribution of Asbily brand assets under misleading developer attribution is prohibited.'
      }
    ]
  },
  '/expense-tracker/open-source-licenses': {
    title: 'Open Source Licenses — Asbily Expense Tracker',
    heading: 'Open Source Licenses',
    subheading: 'Third-Party & Runtime Acknowledgments',
    canonical: '/expense-tracker/open-source-licenses',
    sections: [
      {
        title: 'Express (MIT License)',
        body: 'Copyright (c) TJ Holowaychuk, StrongLoop, and Express contributors. Used under the MIT License to serve the local web application and legal routes.'
      },
      {
        title: 'Plus Jakarta Sans (SIL Open Font License 1.1)',
        body: 'Typography powered by Plus Jakarta Sans (Tokotype), licensed under the SIL Open Font License, Version 1.1.'
      },
      {
        title: 'SVG System & Material Design 3 Patterns',
        body: 'Geometric vector icons and Material 3-inspired interface tokens are implemented directly with inline SVG and standard CSS without external runtime telemetry.'
      }
    ]
  }
};

function renderExpenseTrackerPage(page) {
  const escHtml = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const navLinks = [
    { label: 'Product', href: '/expense-tracker/' },
    { label: 'Privacy', href: '/expense-tracker/privacy' },
    { label: 'Terms', href: '/expense-tracker/terms' },
    { label: 'Asbily Licenses', href: '/expense-tracker/licenses' },
    { label: 'Open Source Licenses', href: '/expense-tracker/open-source-licenses' }
  ];

  const sectionsHtml = page.sections.map((sec) => {
    const bodyHtml = sec.body ? `<p>${escHtml(sec.body)}</p>` : '';
    const listHtml = sec.list ? `<ul>${sec.list.map((li) => `<li>${escHtml(li)}</li>`).join('')}</ul>` : '';
    return `<section class="card"><h2>${escHtml(sec.title)}</h2>${bodyHtml}${listHtml}</section>`;
  }).join('');

  const footerLinksHtml = navLinks.map((item) => {
    const active = item.href === page.canonical ? ' class="active"' : '';
    return `<a href="${item.href}"${active}>${escHtml(item.label)}</a>`;
  }).join('');

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <!-- Google tag (gtag.js) -->
  <script async src="https://www.googletagmanager.com/gtag/js?id=G-T9972RSGEB"></script>
  <script>
    window.dataLayer = window.dataLayer || [];
    function gtag(){dataLayer.push(arguments);}
    gtag('js', new Date());

    gtag('config', 'G-T9972RSGEB');
  </script>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${escHtml(page.title)}</title>
  <link rel="icon" type="image/jpeg" href="/asbily-logo.jpg">
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
  <style>
    :root { --bg:#f5f6f8; --card:#ffffff; --fg:#14171f; --muted:#5f6673; --border:#e3e6ec; --primary:#14171f; --on-primary:#ffffff; }
    @media (prefers-color-scheme: dark) {
      :root { --bg:#0d0f14; --card:#171a22; --fg:#f1f3f7; --muted:#9da5b4; --border:#272c38; --primary:#eef1f6; --on-primary:#12151c; }
    }
    * { box-sizing:border-box; margin:0; padding:0; }
    body { font-family:'Plus Jakarta Sans',system-ui,-apple-system,sans-serif; background:var(--bg); color:var(--fg); line-height:1.6; padding:20px 16px 48px; }
    .wrap { max-width:640px; margin:0 auto; }
    .topbar { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:24px; }
    .brand { display:flex; align-items:center; gap:12px; text-decoration:none; color:inherit; }
    .brand img { width:42px; height:42px; border-radius:12px; object-fit:cover; background:#000; border:1px solid var(--border); }
    .brand-title { font-size:15px; font-weight:700; line-height:1.2; }
    .brand-sub { font-size:12px; color:var(--muted); }
    .btn { display:inline-flex; align-items:center; justify-content:center; min-height:40px; padding:0 16px; border-radius:12px; background:var(--primary); color:var(--on-primary); font-size:13px; font-weight:700; text-decoration:none; }
    .hero { margin-bottom:20px; }
    .hero h1 { font-size:26px; font-weight:800; letter-spacing:-0.4px; }
    .hero p { font-size:13px; color:var(--muted); margin-top:4px; }
    .card { background:var(--card); border:1px solid var(--border); border-radius:18px; padding:18px; margin-bottom:12px; }
    .card h2 { font-size:15px; font-weight:700; margin-bottom:8px; }
    .card p, .card li { font-size:13.5px; color:var(--muted); }
    .card ul { padding-left:18px; display:grid; gap:6px; }
    .dir { display:flex; flex-wrap:wrap; gap:8px 16px; margin-top:20px; padding-top:16px; border-top:1px solid var(--border); }
    .dir a { font-size:13px; font-weight:600; color:var(--muted); text-decoration:none; }
    .dir a.active, .dir a:hover { color:var(--fg); text-decoration:underline; }
    .footer { margin-top:22px; text-align:center; font-size:12.5px; color:var(--muted); }
    .footer strong { display:block; color:var(--fg); font-size:14px; }
  </style>
</head>
<body>
  <div class="wrap">
    <header class="topbar">
      <a class="brand" href="/">
        <img src="/asbily-logo.jpg" alt="Asbily Logo" width="2160" height="2160">
        <div>
          <div class="brand-title">Asbily Expense Tracker</div>
          <div class="brand-sub">Expense by Asbily</div>
        </div>
      </a>
      <a class="btn" href="/?view=about">Open in App</a>
    </header>
    <div class="hero">
      <h1>${escHtml(page.heading)}</h1>
      <p>${escHtml(page.subheading)}</p>
    </div>
    ${sectionsHtml}
    <nav class="dir" aria-label="Asbily Expense Tracker Directory">${footerLinksHtml}</nav>
    <footer class="footer">
      <strong>Asbily</strong>
      <span>Asbily Platforms · com.asbily.expensetracker</span>
    </footer>
  </div>
</body>
</html>`;
}

Object.entries(EXPENSE_TRACKER_PAGES).forEach(([routePath, pageData]) => {
  app.get(routePath, (_req, res) => {
    res.type('html').send(renderExpenseTrackerPage(pageData));
  });
});

app.get('*', (_req, res) => {
  res.sendFile(path.join(__dirname, 'index.html'));
});

app.listen(PORT, HOST, () => {
  console.log(`Server listening on http://${HOST}:${PORT}`);
});
