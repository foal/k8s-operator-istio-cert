package org.jresearch.k8s.operator.istio.certmanager.gateway.model;

import java.util.List;

import io.fabric8.kubernetes.api.model.Duration;

public record CertificateGeneralInfo(String commonName, Duration duration, Duration renewBefore, List<String> usages) {}
