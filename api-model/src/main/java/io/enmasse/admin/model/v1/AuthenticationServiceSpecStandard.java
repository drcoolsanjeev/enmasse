/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.admin.model.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.SecretReference;
import io.sundr.builder.annotations.Buildable;
import io.sundr.builder.annotations.BuildableReference;
import io.sundr.builder.annotations.Inline;

import java.util.Objects;

@Buildable(
        editableEnabled = false,
        generateBuilderPackage = false,
        builderPackage = "io.fabric8.kubernetes.api.builder",
        refs= {
                @BuildableReference(AbstractWithAdditionalProperties.class)
        },
        inline = @Inline(type = Doneable.class, prefix = "Doneable", value = "done")
)
@JsonPropertyOrder({"credentialsSecret", "certificateSecret"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthenticationServiceSpecStandard extends AbstractWithAdditionalProperties {
    private SecretReference credentialsSecret;
    private SecretReference certificateSecret;

    public SecretReference getCredentialsSecret() {
        return credentialsSecret;
    }

    public void setCredentialsSecret(SecretReference credentialsSecret) {
        this.credentialsSecret = credentialsSecret;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthenticationServiceSpecStandard that = (AuthenticationServiceSpecStandard) o;
        return Objects.equals(credentialsSecret, that.credentialsSecret) &&
                Objects.equals(certificateSecret, that.certificateSecret);
    }

    @Override
    public int hashCode() {
        return Objects.hash(credentialsSecret, certificateSecret);
    }

    @Override
    public String toString() {
        return "AuthenticationServiceSpecStandard{" +
                "credentialsSecret=" + credentialsSecret +
                ", certificateSecret=" + certificateSecret +
                '}';
    }

    public SecretReference getCertificateSecret() {
        return certificateSecret;
    }

    public void setCertificateSecret(SecretReference certificateSecret) {
        this.certificateSecret = certificateSecret;
    }
}
