package api

import (
	"net/http"

	"github.com/gin-gonic/gin"

	"attackmode/internal/push"
	"attackmode/internal/store"
)

type subscribeRequest struct {
	Endpoint string `json:"endpoint"`
	Keys     struct {
		P256dh string `json:"p256dh"`
		Auth   string `json:"auth"`
	} `json:"keys"`
	Timezone string `json:"timezone"`
}

func pushKeyHandler(service *push.Service) gin.HandlerFunc {
	return func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"publicKey": service.PublicKey()})
	}
}

func pushSubscribeHandler(st *store.Store) gin.HandlerFunc {
	return func(c *gin.Context) {
		var request subscribeRequest
		if err := c.ShouldBindJSON(&request); err != nil {
			respondError(c, http.StatusBadRequest, "invalid_request", "Invalid request body.")
			return
		}

		err := st.SaveSubscription(c.Request.Context(), store.PushSubscription{
			Endpoint: request.Endpoint,
			P256dh:   request.Keys.P256dh,
			Auth:     request.Keys.Auth,
			Timezone: request.Timezone,
		})
		if err != nil {
			handleStoreError(c, err)
			return
		}
		c.Status(http.StatusNoContent)
	}
}

func pushUnsubscribeHandler(st *store.Store) gin.HandlerFunc {
	return func(c *gin.Context) {
		var request subscribeRequest
		if err := c.ShouldBindJSON(&request); err != nil {
			respondError(c, http.StatusBadRequest, "invalid_request", "Invalid request body.")
			return
		}
		if err := st.DeleteSubscription(c.Request.Context(), request.Endpoint); err != nil {
			handleStoreError(c, err)
			return
		}
		c.Status(http.StatusNoContent)
	}
}

func pushTestHandler(service *push.Service) gin.HandlerFunc {
	return func(c *gin.Context) {
		delivered, err := service.Broadcast(c.Request.Context(), push.Payload{
			Title: "Attack Mode",
			Body:  "Notifications are working. Time to attack.",
			Tag:   "test",
			URL:   "/today",
		})
		if err != nil {
			handleStoreError(c, err)
			return
		}
		c.JSON(http.StatusOK, gin.H{"delivered": delivered})
	}
}
