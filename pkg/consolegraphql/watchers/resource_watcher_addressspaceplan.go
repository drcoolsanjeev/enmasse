/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

// Code generated by go generate; DO NOT EDIT.

package watchers

import (
	"fmt"
	tp "github.com/enmasseproject/enmasse/pkg/apis/admin/v1beta2"
	cp "github.com/enmasseproject/enmasse/pkg/client/clientset/versioned/typed/admin/v1beta2"
	"github.com/enmasseproject/enmasse/pkg/consolegraphql/cache"
	"k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/watch"
	"k8s.io/client-go/rest"
	"log"
	"reflect"
)

type AddressSpacePlanWatcher struct {
	Namespace       string
	cache.Cache
	ClientInterface cp.AdminV1beta2Interface
	watching        chan struct{}
    watchingStarted bool
	stopchan        chan struct{}
	stoppedchan     chan struct{}
}

func (kw *AddressSpacePlanWatcher) Init(c cache.Cache, cl interface{}) error {
	client, ok := cl.(cp.AdminV1beta2Interface)
	if !ok {
		return fmt.Errorf("unexpected type %T", cl)
	}
	kw.Cache = c
	kw.ClientInterface = client
	kw.watching = make(chan struct{})
	kw.stopchan = make(chan struct{})
	kw.stoppedchan = make(chan struct{})
	return nil
}

func (kw *AddressSpacePlanWatcher) Watch() error {
	go func() {
		defer close(kw.stoppedchan)
		defer func() {
			if !kw.watchingStarted {
				close(kw.watching)
			}
		}()
		resource := kw.ClientInterface.AddressSpacePlans(kw.Namespace)
		log.Printf("AddressSpacePlan - Watching")
		running := true
		for running {
			err := kw.doWatch(resource)
			if err != nil {
				log.Printf("AddressSpacePlan - Restarting watch")
			} else {
				running = false
			}
		}
		log.Printf("AddressSpacePlan - Watching stopped")
	}()

	return nil
}

func (kw *AddressSpacePlanWatcher) NewClientForConfig(config *rest.Config) (interface{}, error) {
	return cp.NewForConfig(config)
}

func (kw *AddressSpacePlanWatcher) AwaitWatching() {
	<-kw.watching
}

func (kw *AddressSpacePlanWatcher) Shutdown() {
	close(kw.stopchan)
	<-kw.stoppedchan
}

func (kw *AddressSpacePlanWatcher) doWatch(resource cp.AddressSpacePlanInterface) error {
	resourceList, err := resource.List(v1.ListOptions{})
	if err != nil {
		return err
	}

	curr, err := kw.Cache.GetMap("AddressSpacePlan/", cache.UidKeyAccessor)

	var added = 0
	var updated = 0
	var unchanged = 0
	for _, res := range resourceList.Items {
		copy := res.DeepCopy()
		kw.updateKind(copy)

		if val, ok := curr[copy.UID]; ok {
			if !reflect.DeepEqual(val, copy) {
				err = kw.Cache.Add(copy)
				updated++
				if err != nil {
					return err
				}
			} else {
				unchanged++
			}
			delete(curr, copy.UID)
		} else {
			kw.Cache.Add(copy)
			added++
		}

		kw.Cache.Add(copy)
	}

	// Now remove any stale
	for _, stale := range curr {
		err = kw.Cache.Delete(stale)
		if err != nil {
			return err
		}
	}
	var stale = len(curr)

	log.Printf("AddressSpacePlan - Cache initialised population added %d, updated %d, unchanged %d, stale %d", added, updated, unchanged, stale)
	resourceWatch, err := resource.Watch(v1.ListOptions{
		ResourceVersion: resourceList.ResourceVersion,
	})

	if ! kw.watchingStarted {
		close(kw.watching)
		kw.watchingStarted = true
	}

	ch := resourceWatch.ResultChan()
	for {
		select {
		case event := <-ch:
			var err error
			if event.Type == watch.Error {
				err = fmt.Errorf("Watch ended in error")
			} else {
				res, ok := event.Object.(*tp.AddressSpacePlan)
				log.Printf("AddressSpacePlan - Received event type %s", event.Type)
				if !ok {
					err = fmt.Errorf("Watch error - object of unexpected type received")
				} else {
					copy := res.DeepCopy()
					kw.updateKind(copy)
					switch event.Type {
					case watch.Added:
						err = kw.Cache.Add(copy)
					case watch.Modified:
						err = kw.Cache.Add(copy)
					case watch.Deleted:
						err = kw.Cache.Delete(copy)
					}
				}
			}
			if err != nil {
				return err
			}
		case <-kw.stopchan:
			log.Printf("AddressSpacePlan - Shutdown received")
			return nil
		}
	}
}

func (kw *AddressSpacePlanWatcher) updateKind(o *tp.AddressSpacePlan) {
	if o.TypeMeta.Kind == "" {
		o.TypeMeta.Kind = "AddressSpacePlan"
	}
}
