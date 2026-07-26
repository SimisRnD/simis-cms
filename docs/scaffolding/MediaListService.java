package com.simis.cms.service;

import com.simis.cms.model.MediaAsset;
import com.simis.cms.repository.MediaAssetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * P5: Media List Service
 * Handles querying, filtering, and pagination of media assets
 *
 * Supports:
 * - Pagination (page, size)
 * - Filtering (type, tags)
 * - Search (name and tags)
 * - Sorting (name, size, createdAt)
 */

@Service
public class MediaListService {

  @Autowired
  private MediaAssetRepository mediaAssetRepository;

  /**
   * List media assets with filters and pagination
   *
   * @param page Page number (0-indexed)
   * @param size Items per page
   * @param type Asset type filter: 'image', 'pdf', or null for all
   * @param search Search query (matches name or tags)
   * @param tags Tag filter (comma-separated)
   * @param sortBy Sort field: 'name', 'size', 'createdAt'
   * @param sortOrder Sort direction: 'asc' or 'desc'
   * @return { total, page, size, items: [...] }
   */
  public Map<String, Object> listMediaAssets(
      int page, int size,
      String type,
      String search,
      String tags,
      String sortBy,
      String sortOrder) {

    // Determine sort order
    Sort.Direction direction = "asc".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
    Sort sort = Sort.by(direction, resolveSortField(sortBy));

    // Create pageable
    Pageable pageable = PageRequest.of(page, size, sort);

    // Query database (implementation depends on repository)
    // This is a stub; real implementation would use JPA Specifications or QueryDSL
    Page<MediaAsset> results = mediaAssetRepository.findAll(pageable);

    // TODO: Apply filters to results
    // - type (image/pdf)
    // - search (full-text on name and tags)
    // - tags (filter by comma-separated list)

    // Convert to response format
    List<Map<String, Object>> items = results.getContent().stream()
        .map(asset -> Map.ofEntries(
            Map.entry("assetId", asset.getAssetId()),
            Map.entry("assetName", asset.getAssetName()),
            Map.entry("assetType", asset.getAssetType()),
            Map.entry("fileSize", asset.getFileSizeBytes()),
            Map.entry("mimeType", asset.getMimeType()),
            Map.entry("altText", asset.getAltText()),
            Map.entry("tags", asset.getTags() != null ? asset.getTags() : ""),
            Map.entry("createdAt", asset.getCreatedAt()),
            Map.entry("createdBy", asset.getCreatedBy())
        ))
        .collect(Collectors.toList());

    // Return paginated response
    return Map.ofEntries(
        Map.entry("total", results.getTotalElements()),
        Map.entry("page", page),
        Map.entry("size", size),
        Map.entry("items", items)
    );
  }

  /**
   * Helper: Resolve sort field name
   */
  private String resolveSortField(String sortBy) {
    switch (sortBy) {
      case "name":
        return "assetName";
      case "size":
        return "fileSizeBytes";
      case "createdAt":
      default:
        return "createdAt";
    }
  }

  /**
   * Get a single media asset by ID
   */
  public MediaAsset getMediaAsset(String assetId) {
    return mediaAssetRepository.findByAssetId(assetId)
        .orElseThrow(() -> new IllegalArgumentException("Asset not found"));
  }

  /**
   * Search media by name or tags
   */
  public List<MediaAsset> searchMedia(String query) {
    // TODO: Implement full-text search on name and tags
    // For now, simple substring match on name
    return mediaAssetRepository.findByAssetNameContainingIgnoreCase(query);
  }

  /**
   * Get media by tag
   */
  public List<MediaAsset> getMediaByTag(String tag) {
    // TODO: Implement tag filtering (parse comma-separated tags)
    return mediaAssetRepository.findByTagsContainingIgnoreCase(tag);
  }
}
