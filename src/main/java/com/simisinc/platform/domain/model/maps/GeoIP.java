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

package com.simisinc.platform.domain.model.maps;

import com.simisinc.platform.domain.model.Entity;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/2/18 9:13 AM
 */
public class GeoIP extends Entity {

  private String ipAddress = null;
  private String continent = null;
  private String countryISOCode = null;
  private String country = null;
  private String city = null;
  private String stateISOCode = null;
  private String state = null;
  private String postalCode = null;
  private String timezone = null;
  private double latitude = 0;
  private double longitude = 0;
  private int metroCode = 0;

  public GeoIP() {
  }

  public GeoIP(String ipAddress, String continent, String countryISOCode, String country, String city, String stateISOCode, String state, String postalCode, String timezone, double latitude, double longitude, int metroCode) {
    this.ipAddress = ipAddress;
    this.continent = continent;
    this.countryISOCode = countryISOCode;
    this.country = country;
    this.city = city;
    this.stateISOCode = stateISOCode;
    this.state = state;
    this.postalCode = postalCode;
    this.timezone = timezone;
    this.latitude = latitude;
    this.longitude = longitude;
    this.metroCode = metroCode;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getContinent() {
    return continent;
  }

  public void setContinent(String continent) {
    this.continent = continent;
  }

  public String getCountryISOCode() {
    return countryISOCode;
  }

  public void setCountryISOCode(String countryISOCode) {
    this.countryISOCode = countryISOCode;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getStateISOCode() {
    return stateISOCode;
  }

  public void setStateISOCode(String stateISOCode) {
    this.stateISOCode = stateISOCode;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getPostalCode() {
    return postalCode;
  }

  public void setPostalCode(String postalCode) {
    this.postalCode = postalCode;
  }

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }

  public double getLatitude() {
    return latitude;
  }

  public void setLatitude(double latitude) {
    this.latitude = latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public void setLongitude(double longitude) {
    this.longitude = longitude;
  }

  public int getMetroCode() {
    return metroCode;
  }

  public void setMetroCode(int metroCode) {
    this.metroCode = metroCode;
  }
}
