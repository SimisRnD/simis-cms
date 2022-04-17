/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.simisinc.platform.application.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.datasets.Dataset;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geojson.Feature;
import org.geojson.GeoJsonObject;
import org.geojson.LngLatAlt;
import org.geojson.Polygon;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Reads in dataset rows from a GeoJSON dataset file
 *
 * @author matt rajkowski
 * @created 2/3/19 2:22 PM
 */
public class LoadGeoJsonFeedCommand {

  private static Log LOG = LogFactory.getLog(LoadGeoJsonFeedCommand.class);

  public static List<String[]> loadRows(Dataset dataset, int maxRowCountToReturn) throws DataException {
    File file = DatasetFileCommand.getFile(dataset);
    if (file == null) {
      throw new DataException("Dataset file not found");
    }
    return loadRows(dataset, file, maxRowCountToReturn);
  }

  public static List<GeoJsonObject> loadRecords(Dataset dataset) throws DataException {
    File file = DatasetFileCommand.getFile(dataset);
    if (file == null) {
      return null;
    }
    return loadRecords(dataset, file);
  }

  private static List<String[]> loadRows(Dataset dataset, File file, int maxRowCountToReturn) throws DataException {
    List<String[]> rows = new ArrayList<>();
    if (file == null) {
      return rows;
    }
    try {
      JsonNode config = JsonLoader.fromFile(file);
      Iterator<JsonNode> features = config.get("features").elements();
      for (int i = 0; i < maxRowCountToReturn; i++) {
        if (!features.hasNext()) {
          return rows;
        }
        JsonNode thisFeature = features.next();
        LOG.debug("Got next feature...");
        JsonNode node = thisFeature.get("attributes");
        List<String> fields = new ArrayList<>();
        for (String column : dataset.getColumnNamesList()) {
          String nodeValue = "";
          if (node.has(column)) {
            nodeValue = node.get(column).asText();
          }
          if (nodeValue == null || nodeValue.equalsIgnoreCase("null")) {
            nodeValue = "";
          }
          //nodeValue = StringUtils.abbreviate(nodeValue, 30);
          fields.add(nodeValue);
        }
        rows.add(fields.toArray(new String[0]));
      }
    } catch (Exception e) {
      LOG.error("GeoJson Error: " + e.getMessage());
      throw new DataException("File could not be read");
    }
    return rows;
  }

  private static List<GeoJsonObject> loadRecords(Dataset dataset, File file) throws DataException {
    List<GeoJsonObject> recordList = new ArrayList<>();
    if (file == null) {
      return recordList;
    }
    try {
      JsonNode config = JsonLoader.fromFile(file);
      Iterator<JsonNode> features = config.get("features").elements();

      while (features.hasNext()) {

        JsonNode thisFeature = features.next();
        Feature record = new Feature();

        // Determine the attributes
        JsonNode attributes = thisFeature.get("attributes");
        for (String column : dataset.getColumnNamesList()) {
          String nodeValue = "";
          if (attributes.has(column)) {
            nodeValue = attributes.get(column).asText();
          }
          if (nodeValue == null || nodeValue.equalsIgnoreCase("null")) {
            nodeValue = "";
          }
          record.setProperty(column, nodeValue);
        }

        // Determine the geometry
        if (thisFeature.has("geometry")) {
          JsonNode geometry = thisFeature.get("geometry");
          if (geometry.has("rings")) {
            JsonNode rings = geometry.get("rings");
            if (rings.isContainerNode()) {
              List<LngLatAlt> points = new ArrayList<>();
              Iterator<JsonNode> polygonIterator = rings.elements().next().elements();
              while (polygonIterator.hasNext()) {
                Iterator<JsonNode> pointsIterator = polygonIterator.next().elements();
                LngLatAlt point = new LngLatAlt(pointsIterator.next().asDouble(), pointsIterator.next().asDouble());
                points.add(point);
              }
              Polygon polygon = new Polygon(points);
              record.setGeometry(polygon);
              if (LOG.isDebugEnabled()) {
                LOG.debug("Coordinates: " + points.size() + " and " + ((Polygon) record.getGeometry()).getExteriorRing().size());
              }
            }
          }
        }

        // Determine the centroid
        if (thisFeature.has("centroid")) {
          JsonNode centroid = thisFeature.get("centroid");
          if (centroid.has("x") && centroid.has("y")) {
            record.setProperty("longitude", centroid.get("x").toString());
            record.setProperty("latitude", centroid.get("y").toString());
          }
        }

        recordList.add(record);
      }
    } catch (Exception e) {
      LOG.error("GeoJson Error: " + e.getMessage());
      throw new DataException("File could not be read");
    }
    return recordList;
  }
}
