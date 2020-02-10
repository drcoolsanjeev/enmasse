/*
 * Copyright 2018-2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

// Code generated by client-gen. DO NOT EDIT.

package v1beta1

import (
	v1beta1 "github.com/enmasseproject/enmasse/pkg/apis/admin/v1beta1"
	"github.com/enmasseproject/enmasse/pkg/client/clientset/versioned/scheme"
	rest "k8s.io/client-go/rest"
)

type AdminV1beta1Interface interface {
	RESTClient() rest.Interface
	AuthenticationServicesGetter
	ConsoleServicesGetter
}

// AdminV1beta1Client is used to interact with features provided by the admin.enmasse.io group.
type AdminV1beta1Client struct {
	restClient rest.Interface
}

func (c *AdminV1beta1Client) AuthenticationServices(namespace string) AuthenticationServiceInterface {
	return newAuthenticationServices(c, namespace)
}

func (c *AdminV1beta1Client) ConsoleServices(namespace string) ConsoleServiceInterface {
	return newConsoleServices(c, namespace)
}

// NewForConfig creates a new AdminV1beta1Client for the given config.
func NewForConfig(c *rest.Config) (*AdminV1beta1Client, error) {
	config := *c
	if err := setConfigDefaults(&config); err != nil {
		return nil, err
	}
	client, err := rest.RESTClientFor(&config)
	if err != nil {
		return nil, err
	}
	return &AdminV1beta1Client{client}, nil
}

// NewForConfigOrDie creates a new AdminV1beta1Client for the given config and
// panics if there is an error in the config.
func NewForConfigOrDie(c *rest.Config) *AdminV1beta1Client {
	client, err := NewForConfig(c)
	if err != nil {
		panic(err)
	}
	return client
}

// New creates a new AdminV1beta1Client for the given RESTClient.
func New(c rest.Interface) *AdminV1beta1Client {
	return &AdminV1beta1Client{c}
}

func setConfigDefaults(config *rest.Config) error {
	gv := v1beta1.SchemeGroupVersion
	config.GroupVersion = &gv
	config.APIPath = "/apis"
	config.NegotiatedSerializer = scheme.Codecs.WithoutConversion()

	if config.UserAgent == "" {
		config.UserAgent = rest.DefaultKubernetesUserAgent()
	}

	return nil
}

// RESTClient returns a RESTClient that is used to communicate
// with API server by this client implementation.
func (c *AdminV1beta1Client) RESTClient() rest.Interface {
	if c == nil {
		return nil
	}
	return c.restClient
}
