import { ProviderError } from '../errors.js';

function object(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
}

function text(value) {
  return typeof value === 'string' && value.length ? value : null;
}

function number(value) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function boolean(value) {
  return typeof value === 'boolean' ? value : null;
}

export function normalizeStations(data) {
  const results = object(data).results;
  if (!Array.isArray(results)) throw invalidProviderData('Station search did not return a station list.');
  if (results.some((entry) => !entry || typeof entry !== 'object' || Array.isArray(entry)
    || typeof entry.station_code !== 'string' || typeof entry.station_name !== 'string')) {
    throw invalidProviderData('Station search returned a malformed station.');
  }
  return results.map((entry) => {
    const item = object(entry);
    return { code: text(item.station_code), name: text(item.display_name) || text(item.station_name), city: text(item.city) };
  });
}

export function normalizeBetween(data, requestedRoute) {
  const item = object(data);
  if (typeof item.from_station_code !== 'string' || typeof item.to_station_code !== 'string'
    || (requestedRoute && (item.from_station_code !== requestedRoute.from || item.to_station_code !== requestedRoute.to))
    || !Array.isArray(item.trains)
    || item.trains.some((entry) => !entry || typeof entry !== 'object' || Array.isArray(entry)
      || typeof entry.train_number !== 'string' || typeof entry.train_name !== 'string'
      || typeof entry.departure_time !== 'string' || typeof entry.arrival_time !== 'string'
      || !Number.isFinite(entry.duration_minutes))) {
    throw invalidProviderData('Train search returned an incomplete result.');
  }
  const from = item.from_station_code;
  const to = item.to_station_code;
  return {
    from: { code: from, name: null },
    to: { code: to, name: null },
    count: item.trains.length,
    trains: item.trains.map((entry) => {
      const departureDay = dayOffset(entry.departure_time, 0);
      const arrivalDay = dayOffset(entry.departure_time, entry.duration_minutes);
      return {
        number: text(entry.train_number),
        name: text(entry.train_name),
        type: text(entry.train_type),
        runDays: Array.isArray(entry.running_days) ? entry.running_days : null,
        from: {
          station: { code: from, name: null }, departure: text(entry.departure_time),
          day: departureDay, sequence: null,
        },
        to: {
          station: { code: to, name: null }, arrival: text(entry.arrival_time),
          day: arrivalDay, sequence: null,
        },
        distanceKm: number(entry.distance_km),
        durationMinutes: number(entry.duration_minutes),
        totalHaltsBetween: null,
      };
    }),
  };
}

export function normalizeSchedule(data) {
  const item = object(data);
  if (typeof item.train_number !== 'string' || typeof item.train_name !== 'string' || !Array.isArray(item.stops)
    || item.stops.some((stop) => !stop || typeof stop !== 'object' || Array.isArray(stop)
      || !Number.isFinite(stop.sequence) || typeof stop.station_code !== 'string' || typeof stop.station_name !== 'string')) {
    throw invalidProviderData('Train schedule returned an incomplete route.');
  }
  const route = item.stops;
  return {
    train: {
      number: text(item.train_number), name: text(item.train_name), type: null, category: null,
      source: { code: text(item.source_station_code), name: null },
      destination: { code: text(item.destination_station_code), name: null },
      runDays: Array.isArray(item.running_days) ? item.running_days : null,
      distanceKm: number(item.distance_km), durationMinutes: number(item.total_duration_minutes),
      totalHalts: route.filter((stop) => stop.is_stop === true).length,
    },
    stops: route.map((entry) => {
      const stop = object(entry);
      return {
        sequence: number(stop.sequence),
        station: { code: text(stop.station_code), name: text(stop.station_name), city: null },
        arrival: text(stop.arrival_time), departure: text(stop.departure_time),
        arrivalDay: stop.arrival_time ? dayOffset(stop.arrival_time, 0, stop.day) : null,
        departureDay: stop.departure_time ? dayOffset(stop.departure_time, 0, stop.day) : null,
        distanceKm: number(stop.distance_km), isHalt: boolean(stop.is_stop), platform: text(stop.platform_number),
      };
    }),
  };
}

const FARE_FIELDS = ['baseFare', 'reservationCharge', 'superfastCharge', 'otherCharge', 'tatkalFare', 'goodsServiceTax', 'cateringCharge', 'dynamicFare'];

export function normalizeFare(data, requested) {
  const item = object(data);
  const fares = Array.isArray(item.fares) ? item.fares : [];
  const fare = fares.find((entry) => object(entry).class_code === requested.classCode);
  const breakdown = Object.fromEntries(FARE_FIELDS.map((key) => [key, null]));
  const totalFare = number(object(fare).fare);
  return {
    trainNumber: text(item.train_number) || requested.number, trainName: null,
    source: { code: text(item.from_station_code) || requested.source, name: null },
    destination: { code: text(item.to_station_code) || requested.destination, name: null },
    classCode: requested.classCode, quotaCode: text(item.quota) || requested.quotaCode,
    fareKnown: totalFare !== null, totalFare, breakdown,
  };
}

function classifyAvailability(raw) {
  if (!raw) return 'UNKNOWN';
  const status = raw.trim().toUpperCase();
  if (/^AVAILABLE(?:\b|-)/.test(status)) return 'AVAILABLE';
  if (/^RAC(?:\b|\s|\d)/.test(status)) return 'RAC';
  if (/(?:WL|WAIT\s*LIST)/.test(status)) return 'WAITLIST';
  return 'UNKNOWN';
}

export function normalizeAvailability(data, requested) {
  const item = object(data);
  const classes = Array.isArray(item.classes) ? item.classes : [];
  const result = classes.find((entry) => object(entry).class_code === requested.classCode);
  const rawStatus = text(object(result).availability_text) || text(object(result).status);
  return {
    trainNumber: text(item.train_number) || requested.number, trainName: null,
    source: { code: text(item.from_station_code) || requested.source, name: null },
    destination: { code: text(item.to_station_code) || requested.destination, name: null },
    classCode: requested.classCode, quotaCode: text(item.quota) || requested.quotaCode,
    requestedJourneyDate: requested.journeyDateValue,
    availabilityDate: text(item.journey_date),
    availabilityStatus: rawStatus,
    statusType: classifyAvailability(rawStatus),
  };
}

function dayOffset(time, durationMinutes, journeyDay) {
  if (Number.isFinite(journeyDay)) return Math.max(0, journeyDay - 1);
  const match = /^([01]\d|2[0-3]):([0-5]\d)$/.exec(time || '');
  if (!match) return null;
  return Math.floor((Number(match[1]) * 60 + Number(match[2]) + durationMinutes) / 1440);
}

function invalidProviderData(message) {
  return new ProviderError(502, 'INVALID_PROVIDER_RESPONSE', message);
}
