package org.jresearch.k8s.operator.istio.certmanager.gateway.model;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.Duration;

public record CertificateInfo(List<String> hosts, String credentialName, String namespace, CertificateConfiguration configuration) {

	public Optional<String> getCommonName() {
		return Optional.ofNullable(configuration)
			.map(CertificateConfiguration::info)
			.map(CertificateGeneralInfo::commonName)
			.filter(String::isBlank);
	}

	public Optional<Duration> getDuration() {
		return Optional.ofNullable(configuration)
			.map(CertificateConfiguration::info)
			.map(CertificateGeneralInfo::duration);
	}

	public Optional<Duration> getRenewBefore() {
		return Optional.ofNullable(configuration)
			.map(CertificateConfiguration::info)
			.map(CertificateGeneralInfo::renewBefore);
	}

	public List<String> getUsages() {
		return Optional.ofNullable(configuration)
			.map(CertificateConfiguration::info)
			.map(CertificateGeneralInfo::usages)
			.orElseGet(List::of);
	}
}
