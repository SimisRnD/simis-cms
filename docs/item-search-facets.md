---
id: item-search-facets
title: Item Search Facets
# prettier-ignore
description: How to configure the category and date facets on the item search results widget.
---

`ItemsSearchResultsWidget` can show a facet sidebar next to item search results, letting a visitor narrow results by category or by how recently an item was created, in addition to their search terms. This describes what the facets actually do today and how to configure them, as implemented in `ItemsSearchResultsWidget`.

## What it does

When enabled, the widget renders a sidebar next to the results list with two independent facet groups:

- **Category** — one option per category that has at least one matching item, each showing a count of how many results selecting it would return.
- **Date** — four fixed buckets: last 7 days, last 30 days, last year, and older, computed against the site's configured timezone. Each shows a count the same way.

Both facet groups are **cross-filtered**: a category's count reflects what selecting it would return given every *other* active filter (search terms, the other facet, an active location search), without being narrowed by whatever is currently selected within that same facet group. Selecting a category or date option adds it as an active filter, shown as a removable chip above the results; removing a chip (or a zero-result state) returns to the unfiltered list.

Facet counts respect the same access control as the results themselves — a category from a private collection a visitor can't access never appears, and a selected-but-inaccessible category's name is never disclosed, even indirectly through a facet count.

## Configuring facets

Facets are controlled by four widget preferences, set on the `itemsSearchResults` widget's XML in whichever page template places it (for example `web-templates/page/cms/Search Results.xml`) — there is no admin-UI form for these preferences today, the same as the widget's other settings (`limit`, `useItemLink`).

| Preference | Type | Default | Effect |
|---|---|---|---|
| `showCategoryFacet` | boolean-as-string | `true` | Set to `false` to hide the category facet entirely. |
| `showDateFacet` | boolean-as-string | `true` | Set to `false` to hide the date facet entirely. |
| `categoryFacetLabel` | string | `Category` | Heading shown above the category facet options. |
| `dateFacetLabel` | string | `Date` | Heading shown above the date facet options. |

Both booleans are opt-out, not opt-in — omitting either preference (or setting anything other than the literal string `false`) leaves that facet showing. There is no preference to cap how many options a facet group can show; every category or date bucket with a nonzero count is listed.

Example widget XML enabling both facets with custom labels:

```xml
<widget name="itemsSearchResults">
  <showCategoryFacet>true</showCategoryFacet>
  <categoryFacetLabel>Browse by Type</categoryFacetLabel>
  <showDateFacet>true</showDateFacet>
  <dateFacetLabel>Recency</dateFacetLabel>
</widget>
```

## Known limitations

- Only category and date can be faceted — there's no facet over item tags or custom fields yet.
- The date buckets are fixed (7/30/365 days, older) and not configurable.
- Facets aren't scoped to a specific collection or item type by preference; they reflect whatever the surrounding search is already scoped to.

## Related

- This same category/date facet approach could extend to the other search results widgets (blog, wiki, web page) in a future pass — it's implemented once, on `ItemsSearchResultsWidget` only, today.
