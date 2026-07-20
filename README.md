<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="src/main/webapp/images/logo-white-color.png">
    <img alt="SimIS CMS" src="src/main/webapp/images/logo-header.png" width="360">
  </picture>
</p>

<p align="center">
  <strong>The all-in-one, security-first content platform — pages, commerce, data, and analytics in a single Java application you host yourself.</strong>
</p>

<p align="center">
  <a href="https://github.com/SimisRnD/simis-cms/actions/workflows/ant.yml"><img alt="Java CI" src="https://github.com/SimisRnD/simis-cms/actions/workflows/ant.yml/badge.svg"></a>
  <a href="https://github.com/SimisRnD/simis-cms/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/SimisRnD/simis-cms?display_name=tag&label=release"></a>
  <a href="LICENSE.txt"><img alt="License: Apache-2.0" src="https://img.shields.io/github/license/SimisRnD/simis-cms"></a>
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-orange">
</p>

---

SimIS CMS is an open-source content management system and web portal, written in Java and **run in production by SimIS Inc.** — it's the same platform behind [simisinc.com](https://www.simisinc.com). This isn't a starter kit; it's a complete product you download, deploy, and own.

Everything ships in one platform — pages, blogs, calendars, datasets, e-commerce, CRM, and analytics — with **security built into the core, not bolted on through plug-ins.** Use and configure what's there, and customize what's not.

**Questions and ideas:** [Discussions](https://github.com/SimisRnD/simis-cms/discussions) · **Bugs and feature requests:** [Issues](https://github.com/SimisRnD/simis-cms/issues/new/choose)

## Why SimIS CMS

- **All-in-one.** CMS, e-commerce, CRM, datasets, and analytics in a single deployable — no plug-in sprawl, no version-matching roulette.
- **Secure by default.** Multi-factor authentication, Argon2id password hashing, a built-in firewall and rate limiting, CodeQL static analysis, and a **signed SBOM with build-provenance attestation on every release** — so you know exactly what ships.
- **Privacy-respecting analytics.** First-party and cookieless, with optional IP anonymization and Do-Not-Track / Global Privacy Control honoring. No third parties required.
- **You own it.** Self-hosted, Apache-2.0 licensed, PostgreSQL-backed. Your data stays inside your boundary — a natural fit for government, education, and regulated environments.
- **Built to extend.** Micro-widgets, an expression engine, a workflow engine, and a REST API. Configure it online, or drop to code.

## Quick start

1. **Download** the latest production `.war` from [Releases](https://github.com/SimisRnD/simis-cms/releases/latest).
2. **Start a database** — bring your own PostgreSQL (with PostGIS), or build the bundled one: `docker build -f docker/db/Dockerfile .`
3. **Deploy** the `.war` to a Java 21 servlet container (Tomcat). It runs its own schema migrations on startup — always back up your database first.
4. **Sign in** by adding `/login` to your site URL, then turn on the login setting to reveal a login button.

New releases automatically upgrade a previous install. Full deployment options are in the [documentation](https://github.com/SimisRnD/simis-cms/blob/main/docs/index.md).

## Built for every role

SimIS CMS works from day one, and each role can get straight to work:

- An **administrator** signs in, creates the sitemap, and starts adding pages — alone or with a team.
- A **designer** sets the shared look and layout that every page inherits, then targets page-by-page improvements while authors fill in the content.
- **Content authors** work with searchable text, images, and video, and drop in dynamic elements: slideshows, news feeds, calendar events, blog posts, and more.
- A **developer** extends the platform or builds new functionality, working online or off.

Larger teams can hand off further: built-in Community, Data, and E-commerce Manager roles scope each person's access to just their part of the site.

## Features

* **CMS**: Site map, web pages (templates, UI designer, SEO, searchable) with content and images, HTML editor, CSS editor, blogs, form data, calendars, folders and files, mailing lists, videos, wikis, search, site alerts, form pop-ups, sticky header and buttons, responsive, bot detection
* **Security**: Multi-factor authentication (TOTP + recovery codes), Argon2id password hashing, OAuth, firewall (integration and blocked-IP lists), spam filter, geo filter, rate limiting, CSP/HSTS and session hardening, encrypted secrets at rest, CodeQL and Snyk scanning, and a signed SBOM with build-provenance attestation each release
* **Analytics**: Privacy-first, first-party tracking of sessions, hits, geolocation, content, searches, and referrals; optional cookieless mode, IP anonymization, and Do-Not-Track / GPC honoring; charts; xAPI; pixels
* **Data Integration**: Datasets (CSV, TSV, JSON, GeoJSON, and RSS sources), Collections (profiles, geolocation, multiple categories, relationships, custom fields, indexed, searchable), data sources
* **Collaboration**: Users (register, validation, login, invite), user groups, collection membership and permissions, chat
* **E-commerce**: Products, SKUs, categories, customers, orders, account management, shipping methods, carriers, tracking numbers, pricing rules (constraints, discounts, and promos)
* **CRM**: Forms, leads & customers, orders
* **Settings**: Theme, site SEO, social media, mail server, maps, captcha, analytics, e-commerce, mailing lists
* **Integrations**: Google Analytics, Mapbox, OpenStreetMap, Square, Stripe, TaxJar, USPS, Boxzooka
* **API**: REST API
* **Platform**: Micro-widgets, connection pool, cache, scheduler, workflow, expression engine, upgrades, migrations, record paging

## Documentation

The documentation is written for [MkDocs](https://www.mkdocs.org/) and lives in [`docs/`](https://github.com/SimisRnD/simis-cms/blob/main/docs/index.md).

## Contributing

Pull requests are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md) for how to build the project and what a PR needs. Please report security vulnerabilities privately via a [security advisory](https://github.com/SimisRnD/simis-cms/security/advisories/new); never open a public issue for one.

## License

Apache License 2.0 — see [LICENSE.txt](LICENSE.txt). Copyright 2023–2026 SimIS Inc.

Built on the work of many open-source projects — see [ATTRIBUTION.md](ATTRIBUTION.md) for the full list and their licenses.
