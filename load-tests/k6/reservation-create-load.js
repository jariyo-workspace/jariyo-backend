import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import execution from 'k6/execution';

const PROFILES = {
	base: {
		totalUsers: 15,
		windowSeconds: 30,
		maxDuration: '1m',
		retryShare: 0,
		thresholds: {
			p95: 900,
			p99: 1500,
			errorRate: 0.01,
		},
	},
	stressed: {
		totalUsers: 60,
		windowSeconds: 30,
		maxDuration: '90s',
		retryShare: 0.25,
		thresholds: {
			p95: 2000,
			p99: 3000,
			errorRate: 0.05,
		},
	},
};

const DEFAULT_CASES = [
	{
		name: 'hot-cut-staff-a',
		storeId: '00000000-0000-7000-8000-000000000001',
		serviceId: '00000000-0000-7000-8000-000000009201',
		staffId: '00000000-0000-7000-8000-000000009301',
		fromOffsetDays: 7,
		toOffsetDays: 7,
		partySize: 1,
		focusSlots: 24,
	},
	{
		name: 'hot-cut-staff-b',
		storeId: '00000000-0000-7000-8000-000000000001',
		serviceId: '00000000-0000-7000-8000-000000009201',
		staffId: '00000000-0000-7000-8000-000000009302',
		fromOffsetDays: 7,
		toOffsetDays: 8,
		partySize: 1,
		focusSlots: 24,
	},
	{
		name: 'color-all-staff',
		storeId: '00000000-0000-7000-8000-000000000001',
		serviceId: '00000000-0000-7000-8000-000000009202',
		fromOffsetDays: 8,
		toOffsetDays: 9,
		partySize: 1,
		focusSlots: 32,
	},
	{
		name: 'wide-cut-range',
		storeId: '00000000-0000-7000-8000-000000000001',
		serviceId: '00000000-0000-7000-8000-000000009201',
		fromOffsetDays: 9,
		toOffsetDays: 12,
		partySize: 1,
		focusSlots: 48,
	},
];

const profileName = __ENV.K6_PROFILE || 'base';
const profile = PROFILES[profileName];

if (!profile) {
	throw new Error(`unknown K6_PROFILE: ${profileName}`);
}

const baseUrl = (__ENV.K6_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const randomSeed = Number.parseInt(__ENV.K6_RANDOM_SEED || '29', 10);
const signUpPassword = __ENV.K6_SIGNUP_PASSWORD || 'issue55-load-pass';
const reservationCases = parseCases(__ENV.K6_RESERVATION_CASES);
const jsonHeaders = {
	Accept: 'application/json',
	'Content-Type': 'application/json',
};
const setupFailures = new Counter('setup_failures');
const reservationSemanticFailure = new Rate('reservation_semantic_failure');
const duplicateReplayMismatch = new Rate('duplicate_replay_mismatch');
const duplicateReplaySuccess = new Counter('duplicate_replay_success');

export const options = {
	scenarios: {
		reservation_create: {
			executor: 'per-vu-iterations',
			exec: 'reservationCreate',
			vus: profile.totalUsers,
			iterations: 1,
			maxDuration: profile.maxDuration,
			tags: {
				profile: profileName,
			},
		},
	},
	thresholds: {
		[`http_req_duration{request_name:reservation_create,profile:${profileName},attempt:initial}`]: [
			`p(95)<${profile.thresholds.p95}`,
			`p(99)<${profile.thresholds.p99}`,
		],
		[`reservation_semantic_failure{profile:${profileName}}`]: [`rate<${profile.thresholds.errorRate}`],
		[`duplicate_replay_mismatch{profile:${profileName}}`]: ['rate==0'],
		[`setup_failures{profile:${profileName}}`]: ['count==0'],
		[`checks{profile:${profileName}}`]: ['rate>0.99'],
	},
	summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
	const tokens = [];

	for (let customerNumber = 1; customerNumber <= profile.totalUsers; customerNumber += 1) {
		const token = signUpOrSignIn(customerNumber);
		if (!token) {
			setupFailures.add(1, { profile: profileName });
			continue;
		}
		tokens.push(token);
	}

	return { tokens };
}

export function reservationCreate(data) {
	const vu = execution.vu.idInTest;
	const token = data.tokens[vu - 1];

	if (!token) {
		reservationSemanticFailure.add(1, { profile: profileName });
		return;
	}

	sleep(spreadDelaySeconds(vu));

	const scenario = selectScenarioCase(vu);
	const availableSlot = lookupSlot(scenario, token, vu);
	if (!availableSlot) {
		reservationSemanticFailure.add(1, { profile: profileName });
		return;
	}

	const key = `issue55-${profileName}-${vu}`;
	const payload = JSON.stringify({
		storeId: scenario.storeId,
		serviceId: scenario.serviceId,
		staffId: availableSlot.staffId,
		startAt: availableSlot.startAt,
		partySize: scenario.partySize || 1,
		customerNote: `load-test-${profileName}-${vu}`,
	});
	const initial = createReservation(token, key, payload, scenario, 'initial');

	if (shouldReplay(vu) && initial.ok) {
		sleep(1);
		const replay = createReservation(token, key, payload, scenario, 'replay');
		const replayMatches = replay.ok && replay.reservationId === initial.reservationId;

		if (replayMatches) {
			duplicateReplaySuccess.add(1, { profile: profileName });
		}
		duplicateReplayMismatch.add(replayMatches ? 0 : 1, { profile: profileName });
		check(replay.response, {
			'replay returns same reservation id': () => replayMatches,
		}, {
			profile: profileName,
		});
	}
}

function signUpOrSignIn(customerNumber) {
	const credentials = customerCredentials(customerNumber);
	const signUpResponse = http.post(`${baseUrl}/api/v1/auth/sign-up`, JSON.stringify({
		email: credentials.email,
		password: signUpPassword,
		displayName: credentials.displayName,
		phoneNumber: credentials.phoneNumber,
		agreements: {
			terms: true,
			privacy: true,
			marketing: false,
		},
	}), {
		headers: jsonHeaders,
		tags: {
			profile: profileName,
			request_name: 'auth_sign_up',
		},
	});

	if (signUpResponse.status === 201) {
		return accessToken(signUpResponse);
	}

	const signInResponse = http.post(`${baseUrl}/api/v1/auth/sign-in`, JSON.stringify({
		email: credentials.email,
		password: signUpPassword,
	}), {
		headers: jsonHeaders,
		tags: {
			profile: profileName,
			request_name: 'auth_sign_in',
		},
	});

	return signInResponse.status === 200 ? accessToken(signInResponse) : null;
}

function customerCredentials(customerNumber) {
	const suffix = String(customerNumber).padStart(3, '0');
	return {
		email: `issue55-${profileName}-${suffix}@example.com`,
		displayName: `예약부하고객${suffix}`,
		phoneNumber: `0105500${suffix}${suffix.slice(1, 2)}`,
	};
}

function lookupSlot(scenario, token, vu) {
	const query = buildAvailabilityQuery(scenario);
	const response = http.get(`${baseUrl}/api/v1/stores/${scenario.storeId}/availability?${query}`, {
		headers: {
			Accept: 'application/json',
			Authorization: `Bearer ${token}`,
		},
		tags: {
			profile: profileName,
			request_name: 'availability_lookup',
			case_name: scenario.name,
		},
	});

	const slots = flattenSlots(response);
	const selectedSlot = selectSlot(slots, scenario, vu);
	const ok = response.status === 200 && selectedSlot !== null;

	check(response, {
		'availability lookup succeeded': () => ok,
	}, {
		profile: profileName,
	});

	return ok ? selectedSlot : null;
}

function createReservation(token, key, payload, scenario, attempt) {
	const response = http.post(`${baseUrl}/api/v1/reservations`, payload, {
		headers: {
			...jsonHeaders,
			Authorization: `Bearer ${token}`,
			'Idempotency-Key': key,
		},
		tags: {
			profile: profileName,
			request_name: 'reservation_create',
			case_name: scenario.name,
			attempt,
		},
	});

	const reservationId = accessValue(response, 'data.id');
	const ok = response.status === 201 && typeof reservationId === 'string' && reservationId.length > 0;

	reservationSemanticFailure.add(ok ? 0 : 1, { profile: profileName });
	check(response, {
		'reservation create succeeded': () => ok,
	}, {
		profile: profileName,
	});

	return {
		ok,
		reservationId,
		response,
	};
}

function parseCases(raw) {
	if (!raw) {
		return DEFAULT_CASES;
	}
	try {
		const parsed = JSON.parse(raw);
		if (!Array.isArray(parsed) || parsed.length === 0) {
			throw new Error('K6_RESERVATION_CASES must be a non-empty JSON array');
		}
		return parsed;
	} catch (error) {
		throw new Error(`failed to parse K6_RESERVATION_CASES: ${error.message}`);
	}
}

function selectScenarioCase(vu) {
	return reservationCases[(vu - 1 + randomSeed) % reservationCases.length];
}

function buildAvailabilityQuery(scenario) {
	const now = new Date();
	const from = offsetDate(now, scenario.fromOffsetDays || 0);
	const to = offsetDate(now, scenario.toOffsetDays || scenario.fromOffsetDays || 0);
	const query = [
		['serviceId', scenario.serviceId],
		['from', from],
		['to', to],
		['partySize', String(scenario.partySize || 1)],
	];

	if (scenario.staffId) {
		query.push(['staffId', scenario.staffId]);
	}

	return query.map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`).join('&');
}

function flattenSlots(response) {
	try {
		const dates = response.json('data.dates');
		if (!Array.isArray(dates)) {
			return [];
		}
		return dates.flatMap((date) => Array.isArray(date.slots) ? date.slots : []);
	} catch (_) {
		return [];
	}
}

function selectSlot(slots, scenario, vu) {
	if (slots.length === 0) {
		return null;
	}

	const focusSlots = Math.max(1, Math.min(scenario.focusSlots || slots.length, slots.length));
	const caseTurn = Math.floor((vu - 1) / reservationCases.length);
	const index = caseTurn % focusSlots;
	return slots[index];
}

function shouldReplay(vu) {
	if (profile.retryShare <= 0) {
		return false;
	}
	const modulo = Math.max(1, Math.round(1 / profile.retryShare));
	return vu % modulo === 0;
}

function spreadDelaySeconds(vu) {
	return (vu * 7 + randomSeed) % profile.windowSeconds;
}

function offsetDate(now, offsetDays) {
	const kstNow = new Date(now.getTime() + 9 * 60 * 60 * 1000);
	const shifted = new Date(kstNow.getTime() + offsetDays * 24 * 60 * 60 * 1000);
	const year = shifted.getUTCFullYear();
	const month = String(shifted.getUTCMonth() + 1).padStart(2, '0');
	const day = String(shifted.getUTCDate()).padStart(2, '0');
	return `${year}-${month}-${day}`;
}

function accessToken(response) {
	const token = accessValue(response, 'data.accessToken');
	return typeof token === 'string' && token.length > 0 ? token : null;
}

function accessValue(response, path) {
	try {
		return response.json(path);
	} catch (_) {
		return null;
	}
}

export function handleSummary(data) {
	const lines = [
		`# Issue #55 reservation create load test (${profileName})`,
		'',
		'## Key metrics',
		`- setup failures: ${metricCount(data, 'setup_failures')}`,
		`- semantic failure rate: ${metricRate(data, 'reservation_semantic_failure')}`,
		`- duplicate replay mismatch rate: ${metricRate(data, 'duplicate_replay_mismatch')}`,
		`- duplicate replay success count: ${metricCount(data, 'duplicate_replay_success')}`,
		`- reservation create p95: ${metricPercentile(data, `http_req_duration{request_name:reservation_create,profile:${profileName},attempt:initial}`, 'p(95)')} ms`,
		`- reservation create p99: ${metricPercentile(data, `http_req_duration{request_name:reservation_create,profile:${profileName},attempt:initial}`, 'p(99)')} ms`,
		'',
	].join('\n');

	return {
		stdout: lines,
	};
}

function metricCount(data, metricName) {
	return data.metrics?.[metricName]?.values?.count ?? 0;
}

function metricRate(data, metricName) {
	return data.metrics?.[metricName]?.values?.rate ?? 0;
}

function metricPercentile(data, metricName, percentile) {
	return data.metrics?.[metricName]?.values?.[percentile] ?? 0;
}
