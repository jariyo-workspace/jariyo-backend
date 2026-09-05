import http from 'k6/http';
import { check } from 'k6';

const PROFILES = {
	base: {
		totalUsers: 30,
		requestsPerVu: 3,
		maxDuration: '20s',
		thresholds: {
			p95: 700,
			p99: 1200,
			errorRate: 0.01,
		},
	},
	stressed: {
		totalUsers: 120,
		requestsPerVu: 5,
		maxDuration: '20s',
		thresholds: {
			p95: 1500,
			p99: 2500,
			errorRate: 0.03,
		},
	},
};

const DEFAULT_CASES = [
	{
		name: 'hot-staff-a',
		storeId: '00000000-0000-7000-8000-000000000001',
		serviceId: '00000000-0000-7000-8000-000000009201',
		staffId: '00000000-0000-7000-8000-000000009301',
		fromOffsetDays: 7,
		toOffsetDays: 9,
		partySize: 1,
	},
	{
		name: 'hot-staff-b',
		storeId: '00000000-0000-7000-8000-000000000001',
		serviceId: '00000000-0000-7000-8000-000000009201',
		staffId: '00000000-0000-7000-8000-000000009302',
		fromOffsetDays: 7,
		toOffsetDays: 8,
		partySize: 1,
	},
	{
		name: 'service-all-staff',
		storeId: '00000000-0000-7000-8000-000000000001',
		serviceId: '00000000-0000-7000-8000-000000009202',
		fromOffsetDays: 8,
		toOffsetDays: 10,
		partySize: 1,
	},
	{
		name: 'wide-range',
		storeId: '00000000-0000-7000-8000-000000000001',
		serviceId: '00000000-0000-7000-8000-000000009201',
		fromOffsetDays: 7,
		toOffsetDays: 13,
		partySize: 1,
	},
];

const profileName = __ENV.K6_PROFILE || 'base';
const profile = PROFILES[profileName];

if (!profile) {
	throw new Error(`unknown K6_PROFILE: ${profileName}`);
}

const authToken = __ENV.K6_AUTH_TOKEN;
const includeMemberScenario = authToken && authToken.trim().length > 0;
const baseUrl = (__ENV.K6_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const randomSeed = Number.parseInt(__ENV.K6_RANDOM_SEED || '17', 10);
const availabilityCases = parseCases(__ENV.K6_AVAILABILITY_CASES);
const guestVus = Math.ceil(profile.totalUsers / (includeMemberScenario ? 2 : 1));
const memberVus = includeMemberScenario ? Math.floor(profile.totalUsers / 2) : 0;

export const options = {
	scenarios: buildScenarios(profileName, profile, guestVus, memberVus),
	thresholds: {
		[`http_req_duration{profile:${profileName}}`]: [
			`p(95)<${profile.thresholds.p95}`,
			`p(99)<${profile.thresholds.p99}`,
		],
		[`http_req_failed{profile:${profileName}}`]: [`rate<${profile.thresholds.errorRate}`],
		[`checks{profile:${profileName}}`]: ['rate>0.99'],
	},
	summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

export function guestAvailability() {
	runAvailabilityScenario('guest', null);
}

export function memberAvailability() {
	runAvailabilityScenario('member', authToken);
}

function buildScenarios(name, activeProfile, guestUsers, memberUsers) {
	const scenarios = {
		guest: {
			executor: 'per-vu-iterations',
			exec: 'guestAvailability',
			vus: guestUsers,
			iterations: activeProfile.requestsPerVu,
			maxDuration: activeProfile.maxDuration,
			tags: {
				profile: name,
				user_type: 'guest',
			},
		},
	};
	if (memberUsers > 0) {
		scenarios.member = {
			executor: 'per-vu-iterations',
			exec: 'memberAvailability',
			vus: memberUsers,
			iterations: activeProfile.requestsPerVu,
			maxDuration: activeProfile.maxDuration,
			tags: {
				profile: name,
				user_type: 'member',
			},
		};
	}
	return scenarios;
}

function runAvailabilityScenario(userType, token) {
	const scenario = selectScenarioCase(__VU, __ITER, userType);
	const query = buildQuery(scenario);
	const params = {
		headers: {
			Accept: 'application/json',
			...(token ? { Authorization: `Bearer ${token}` } : {}),
		},
		tags: {
			profile: profileName,
			user_type: userType,
			case_name: scenario.name,
			store_id: scenario.storeId,
			service_id: scenario.serviceId,
			has_staff_filter: String(Boolean(scenario.staffId)),
		},
	};

	const response = http.get(
		`${baseUrl}/api/v1/stores/${scenario.storeId}/availability?${query.toString()}`,
		params,
	);

	check(response, {
		'status is 200': (res) => res.status === 200,
		'response has dates': (res) => {
			const body = safeJson(res);
			return Array.isArray(body?.data?.dates);
		},
		'response contains slots array': (res) => {
			const body = safeJson(res);
			return body?.data?.dates?.every((date) => Array.isArray(date.slots)) ?? false;
		},
	}, {
		profile: profileName,
		user_type: userType,
	});
}

function parseCases(raw) {
	if (!raw) {
		return DEFAULT_CASES;
	}
	try {
		const parsed = JSON.parse(raw);
		if (!Array.isArray(parsed) || parsed.length === 0) {
			throw new Error('K6_AVAILABILITY_CASES must be a non-empty JSON array');
		}
		return parsed;
	} catch (error) {
		throw new Error(`failed to parse K6_AVAILABILITY_CASES: ${error.message}`);
	}
}

function selectScenarioCase(vu, iteration, userType) {
	const mix = deterministicPick(vu, iteration, userType);
	if (mix < 0.7) {
		return availabilityCases[0];
	}
	const index = (vu + iteration + (userType === 'member' ? 1 : 0)) % availabilityCases.length;
	return availabilityCases[index];
}

function deterministicPick(vu, iteration, userType) {
	const userOffset = userType === 'member' ? 13 : 7;
	const value = (vu * 37 + iteration * 17 + userOffset + randomSeed) % 100;
	return value / 100;
}

function buildQuery(scenario) {
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

function offsetDate(now, offsetDays) {
	const kstNow = new Date(now.getTime() + 9 * 60 * 60 * 1000);
	const shifted = new Date(kstNow.getTime() + offsetDays * 24 * 60 * 60 * 1000);
	const year = shifted.getUTCFullYear();
	const month = String(shifted.getUTCMonth() + 1).padStart(2, '0');
	const day = String(shifted.getUTCDate()).padStart(2, '0');
	return `${year}-${month}-${day}`;
}

function safeJson(response) {
	try {
		return response.json();
	} catch (error) {
		return null;
	}
}
