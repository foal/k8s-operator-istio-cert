package org.jresearch.k8s.operator.istio.certmanager.gateway;

import static io.fabric8.kubernetes.client.utils.KubernetesResourceUtil.getQualifiedName;

import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.jresearch.k8s.operator.istio.certmanager.gateway.model.CertManagerIngressAnnotation;
import org.jresearch.k8s.operator.istio.certmanager.gateway.model.CertificateConfiguration;
import org.jresearch.k8s.operator.istio.certmanager.gateway.model.CertificateGeneralInfo;
import org.jresearch.k8s.operator.istio.certmanager.gateway.model.CertificateInfo;
import org.jresearch.k8s.operator.istio.certmanager.gateway.model.CertificateIssuerInfo;

import io.fabric8.certmanager.api.model.meta.v1.ObjectReference;
import io.fabric8.certmanager.api.model.meta.v1.ObjectReferenceBuilder;
import io.fabric8.certmanager.api.model.v1.Certificate;
import io.fabric8.certmanager.api.model.v1.CertificateBuilder;
import io.fabric8.certmanager.api.model.v1.CertificateSpec;
import io.fabric8.certmanager.api.model.v1.CertificateSpecBuilder;
import io.fabric8.certmanager.client.CertManagerClient;
import io.fabric8.kubernetes.api.model.Duration;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.quarkus.logging.Log;
import me.snowdrop.istio.api.networking.v1beta1.GatewaySpec;
import me.snowdrop.istio.api.networking.v1beta1.Server;
import me.snowdrop.istio.api.networking.v1beta1.ServerTLSSettings;
import one.util.streamex.StreamEx;

@Singleton
@Controller
public class GatewayController implements ResourceController<Gateway> {

	@Inject
	KubernetesClient kubernetesClient;

	@Override
	public DeleteControl deleteResource(Gateway gateway, Context<Gateway> context) {
		Log.tracef("Execution deleteResource for: %s", getQualifiedName(gateway));
		List<CertificateInfo> hosts = getCertificateInfo(gateway);
		hosts.forEach(host -> Log.infof("Remove certificate for: %s", host));
		return DeleteControl.DEFAULT_DELETE;
	}

	@Override
	public UpdateControl<Gateway> createOrUpdateResource(Gateway gateway, Context<Gateway> context) {
		Log.tracef("Execution createOrUpdateResource for: %s", getQualifiedName(gateway));

		List<CertificateInfo> infos = getCertificateInfo(gateway);
		infos.forEach(info -> createOrUpdateCertificate(gateway, info));

		return UpdateControl.noUpdate();
	}

	@SuppressWarnings("resource")
	private List<CertificateInfo> getCertificateInfo(Gateway gw) {
		Optional<CertificateConfiguration> certificateConfiguration = getGeneralCertificateConfiguration(gw);
		if (certificateConfiguration.isEmpty()) {
			return List.of();
		}

		String namespace = kubernetesClient
			.apps()
			.deployments()
			.inAnyNamespace()
			.withLabels(gw.getSpec().getSelector())
			.list()
			.getItems()
			.stream()
			.findAny()
			.map(Deployment::getMetadata)
			.map(ObjectMeta::getNamespace)
			.orElse(null);
		if (namespace == null) {
			Log.warnf("Can't find targer (istio ingress) namespace for GW %s", getQualifiedName(gw));
			return List.of();
		}

		Log.debugf("Gateway %s, detected namespace %s", getQualifiedName(gw), namespace);

		List<Server> servers = Optional.of(gw)
			.map(Gateway::getSpec)
			.map(GatewaySpec::getServers)
			.orElseGet(List::of);

		return StreamEx.of(servers)
			.map(srv -> map(srv, namespace, certificateConfiguration.get()))
			.nonNull()
			.toImmutableList();

	}

	private static Optional<CertificateConfiguration> getGeneralCertificateConfiguration(Gateway gw) {
		Map<String, String> annotations = gw.getMetadata().getAnnotations();
		Optional<CertificateIssuerInfo> issuer = getCertificateIssuerInfo(gw, annotations);
		if (issuer.isEmpty()) {
			return Optional.empty();
		}
		CertificateGeneralInfo certificateGeneralInfo = getCertificateGeneralInfo(annotations);
		return Optional.of(new CertificateConfiguration(issuer.get(), certificateGeneralInfo));
	}

	@SuppressWarnings("resource")
	private static CertificateGeneralInfo getCertificateGeneralInfo(Map<String, String> annotations) {
		String commonName = annotations.getOrDefault(CertManagerIngressAnnotation.COMMON_NAME.getName(), "");
		Duration duration = Optional.ofNullable(annotations.get(CertManagerIngressAnnotation.DURATION.getName()))
			.map(GatewayController::parse)
			.orElse(null);
		Duration renewBefore = Optional.ofNullable(annotations.get(CertManagerIngressAnnotation.RENEW_BEFORE.getName()))
			.map(GatewayController::parse)
			.orElse(null);
		List<String> usages = Optional.ofNullable(annotations.get(CertManagerIngressAnnotation.USAGES.getName()))
			.map(u -> StreamEx.split(u, ','))
			.orElseGet(StreamEx::empty)
			.map(String::trim)
			.remove(String::isEmpty)
			.toImmutableList();
		return new CertificateGeneralInfo(commonName, duration, renewBefore, usages);
	}

	private static Duration parse(String duration) {
		try {
			return Duration.parse(duration);
		} catch (ParseException e) {
			Log.warnf("Can't parse duration from string %s: %s", duration, e.getMessage());
			return null;
		}
	}

	private static Optional<CertificateIssuerInfo> getCertificateIssuerInfo(HasMetadata resource, Map<String, String> annotations) {
		String certificateKind = "Issuer";
		String certificateGroup = "cert-manager.io";

		String clusterIssuer = annotations.get(CertManagerIngressAnnotation.CLUSTER_ISSUER.getName());
		String issuer = annotations.get(CertManagerIngressAnnotation.ISSUER.getName());
		String kind = annotations.get(CertManagerIngressAnnotation.ISSUER_KIND.getName());
		String group = annotations.get(CertManagerIngressAnnotation.ISSUER_GROUP.getName());
		if (clusterIssuer == null && issuer == null) {
			Log.debugf("No issuer annotation ignore resource %s", getQualifiedName(resource));
			// No issuer annotation ignore resource
			return Optional.empty();
		} else if (clusterIssuer != null && clusterIssuer.isBlank() || issuer != null && issuer.isBlank()) {
			Log.errorf("Can't determinate issuer, issuer or clusterIssuer name is blank, resource %s", getQualifiedName(resource));
			return Optional.empty();
		} else if (clusterIssuer != null) {
			certificateKind = "ClusterIssuer";
			if (issuer != null || kind != null || group != null) {
				Log.errorf("ClisterIssuer annotation can't be used in conjunction with Issuer, Group or Kind annotations on the same resource %s", getQualifiedName(resource));
				return Optional.empty();
			}
			issuer = clusterIssuer;
		} else {
			if (kind != null) {
				if (kind.isBlank()) {
					Log.errorf("Kind annotation is blank, resource %s", getQualifiedName(resource));
					return Optional.empty();
				}
				certificateKind = kind;
			} else if (group != null) {
				if (group.isBlank()) {
					Log.errorf("Group annotation is blank, resource %s", getQualifiedName(resource));
					return Optional.empty();
				}
				certificateGroup = group;
			}
		}
		return Optional.of(new CertificateIssuerInfo(issuer, certificateKind, certificateGroup));
	}

	@SuppressWarnings("resource")
	private static CertificateInfo map(Server server, String namespace, CertificateConfiguration info) {
		Optional<Server> srv = Optional.of(server);
		String credentialName = srv.map(Server::getTls).map(ServerTLSSettings::getCredentialName).orElse(null);
		if (credentialName == null) {
			return null;
		}
		List<String> hosts = srv
			.map(Server::getHosts)
			.orElseGet(List::of);
		List<String> filteredHosts = StreamEx.of(hosts)
			.remove("*"::equals)
			.toImmutableList();
		if (filteredHosts.isEmpty()) {
			return null;
		}
		return new CertificateInfo(hosts, credentialName, namespace, info);
	}

	@SuppressWarnings("resource")
	private Certificate createOrUpdateCertificate(Gateway gw, CertificateInfo info) {
		CertManagerClient certManagerClient = kubernetesClient.adapt(CertManagerClient.class);

		Certificate existingCertificate = certManagerClient.v1().certificates().inNamespace(info.namespace()).withName(info.credentialName()).get();

		if (existingCertificate == null) {
			Log.infof("New certificate for %s will be created with parameters %s", getQualifiedName(gw), info);
			Certificate certificate = new CertificateBuilder()
				.withMetadata(createMetadata(info))
				.withSpec(createSpec(info))
				.build();

			return certManagerClient.v1().certificates().inNamespace(info.namespace()).create(certificate);
		}
		Certificate updatedCertificate = update(existingCertificate, info);
		if (!existingCertificate.equals(updatedCertificate)) {
			Log.infof("Existing certificate for %s will be updated with parameters %s", getQualifiedName(gw), info);
			return certManagerClient.v1().certificates().inNamespace(info.namespace()).withName(info.credentialName()).edit(exCert -> updatedCertificate);
		}
		Log.infof("Existing certificate for %s is up to date with parameters %s", getQualifiedName(gw), info);
		return existingCertificate;
	}

	private static Certificate update(Certificate exCert, CertificateInfo info) {
		return new CertificateBuilder(exCert)
			.withSpec(updateSpec(exCert.getSpec(), info))
			.build();
	}

	private static CertificateSpec updateSpec(CertificateSpec spec, CertificateInfo info) {
		// Required part
		CertificateSpecBuilder builder = new CertificateSpecBuilder(spec)
			.withIssuerRef(createIssuerRef(info))
			.withDnsNames(info.hosts())
			.withSecretName(info.credentialName());
		// Optional part
		info.getCommonName().ifPresentOrElse(builder::withCommonName, () -> builder.withCommonName(null));
		info.getDuration().ifPresentOrElse(builder::withDuration, () -> builder.withDuration(null));
		info.getRenewBefore().ifPresentOrElse(builder::withRenewBefore, () -> builder.withRenewBefore(null));
		// Overwrite - we don't know how to merge
		builder.withUsages(info.getUsages());
		return builder.build();
	}

	private static CertificateSpec createSpec(CertificateInfo info) {
		// Required part
		CertificateSpecBuilder builder = new CertificateSpecBuilder()
			.withIssuerRef(createIssuerRef(info))
			.withDnsNames(info.hosts())
			.withSecretName(info.credentialName());
		// Optional part
		info.getCommonName().ifPresent(builder::withCommonName);
		info.getDuration().ifPresent(builder::withDuration);
		info.getRenewBefore().ifPresent(builder::withRenewBefore);
		if (!info.getUsages().isEmpty()) {
			builder.withUsages(info.getUsages());
		}
		return builder.build();
	}

	private static ObjectReference createIssuerRef(CertificateInfo info) {
		return new ObjectReferenceBuilder()
			.withName(info.configuration().issuer().name())
			.withKind(info.configuration().issuer().kind())
			.withGroup(info.configuration().issuer().group())
			.build();
	}

	private static ObjectMeta createMetadata(CertificateInfo info) {
		return new ObjectMetaBuilder()
			.withName(info.credentialName())
			.build();
	}

}