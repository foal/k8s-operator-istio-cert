package org.jresearch.k8s.operator.istio.certmanager.gateway.model;

public enum CertManagerIngressAnnotation {

	ISSUER("cert-manager.io/issuer"),
	CLUSTER_ISSUER("cert-manager.io/cluster-issuer"),
	ISSUER_KIND("cert-manager.io/issuer-kind"),
	ISSUER_GROUP("cert-manager.io/issuer-group"),
	COMMON_NAME("cert-manager.io/common-name"),
	DURATION("cert-manager.io/duration"),
	RENEW_BEFORE("cert-manager.io/renew-before"),
	USAGES("cert-manager.io/usages"),
	;

	private final String name;

	private CertManagerIngressAnnotation(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

}
