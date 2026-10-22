import { Router } from 'express';
import { ApiError } from '../errors.js';
import { normalizeAvailability, normalizeBetween, normalizeFare, normalizeSchedule, normalizeStations } from '../provider/normalize.js';
import { CLASS_CODES, enumValue, journeyDate, queryString, QUOTA_CODES, stationCode, stationLimit, trainNumber } from '../validation.js';

export function createApiRouter(client) {
  const router = Router();

  router.get('/stations/search', async (req, res) => {
    const q = queryString(req.query.q);
    const limit = stationLimit(req.query.limit);
    const data = await client.get('stations/search', { q, limit });
    res.json({ success: true, data: { stations: normalizeStations(data) }, meta: { requestId: req.id } });
  });

  router.get('/trains/between', async (req, res) => {
    const from = stationCode(req.query.from, 'From station');
    const to = stationCode(req.query.to, 'To station');
    if (from === to) throw new ApiError(400, 'INVALID_INPUT', 'From and to stations must be different.');
    const date = journeyDate(req.query.date);
    const data = await client.get('routes/trains', { from, to, date });
    res.json({ success: true, data: normalizeBetween(data, { from, to }), meta: { requestId: req.id } });
  });

  router.get('/trains/:number/schedule', async (req, res) => {
    const number = trainNumber(req.params.number);
    const data = await client.get(`trains/${encodeURIComponent(number)}/schedule`);
    res.json({ success: true, data: normalizeSchedule(data), meta: { requestId: req.id } });
  });

  router.get('/trains/:number/fare', async (req, res) => {
    const number = trainNumber(req.params.number);
    const source = stationCode(req.query.source, 'Source station');
    const destination = stationCode(req.query.destination, 'Destination station');
    if (source === destination) throw new ApiError(400, 'INVALID_INPUT', 'Source and destination must be different.');
    const journeyDateValue = journeyDate(req.query.journeyDate);
    const classCode = enumValue(req.query.classCode, 'Class code', CLASS_CODES);
    const quotaCode = req.query.quotaCode === undefined ? 'GN' : enumValue(req.query.quotaCode, 'Quota code', QUOTA_CODES);
    const data = await client.get('fares/estimate', {
      train_number: number, from: source, to: destination, class: classCode, quota: quotaCode,
    });
    res.json({ success: true, data: { ...normalizeFare(data, { number, source, destination, classCode, quotaCode }), requestedJourneyDate: journeyDateValue }, meta: { requestId: req.id } });
  });

  router.get('/trains/:number/availability', async (req, res) => {
    const number = trainNumber(req.params.number);
    const source = stationCode(req.query.source, 'Source station');
    const destination = stationCode(req.query.destination, 'Destination station');
    if (source === destination) throw new ApiError(400, 'INVALID_INPUT', 'Source and destination must be different.');
    const journeyDateValue = journeyDate(req.query.journeyDate);
    const classCode = enumValue(req.query.classCode, 'Class code', CLASS_CODES);
    const quotaCode = req.query.quotaCode === undefined ? 'GN' : enumValue(req.query.quotaCode, 'Quota code', QUOTA_CODES);
    const data = await client.get('availability/seats', {
      train_number: number, from: source, to: destination, date: journeyDateValue, class: classCode, quota: quotaCode,
    });
    res.json({ success: true, data: normalizeAvailability(data, { number, source, destination, journeyDateValue, classCode, quotaCode }), meta: { requestId: req.id } });
  });

  return router;
}
