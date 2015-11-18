package org.bluebank.resource;


import com.google.protobuf.InvalidProtocolBufferException;
import org.bluebank.api.endpoint.InboundEndPoint;
import org.bluebank.contract.Messages.DepositRequest;
import org.bluebank.contract.Messages.InquiryRequest;
import org.bluebank.contract.Messages.ValidateCardRequest;
import org.bluebank.contract.Messages.ValidatePinRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.util.HashSet;
import java.util.Set;

import static com.google.common.base.Throwables.propagate;
import static java.nio.ByteBuffer.wrap;
import static java.util.Collections.synchronizedSet;
import static org.bluebank.contract.Messages.Message;

@Singleton
@ServerEndpoint(value = "/events")
public class AtmResource {

    private static final Set<Session> sessions = synchronizedSet(new HashSet<>());
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final InboundEndPoint<ValidateCardRequest> validateCardRequestReceiver;
    private final InboundEndPoint<ValidatePinRequest> validatePinRequestReceiver;
    private final InboundEndPoint<DepositRequest> depositRequestReceiver;
    private final InboundEndPoint<InquiryRequest> inquiryRequestReceiver;

    @Inject
    public AtmResource(@Named("validateCardRequestReceiver") InboundEndPoint<ValidateCardRequest> validateCardRequestReceiver,
                       @Named("validatePinRequestReceiver") InboundEndPoint<ValidatePinRequest> validatePinRequestReceiver,
                       @Named("depositRequestReceiver") InboundEndPoint<DepositRequest> depositRequestReceiver,
                       @Named("inquiryRequestReceiver") InboundEndPoint<InquiryRequest> inquiryRequestReceiver) {
        this.validateCardRequestReceiver = validateCardRequestReceiver;
        this.validatePinRequestReceiver = validatePinRequestReceiver;
        this.depositRequestReceiver = depositRequestReceiver;
        this.inquiryRequestReceiver = inquiryRequestReceiver;
    }

    @OnOpen
    public void onOpen(Session session) {
        logger.info("Session ID : {} - Connection opened", session.getId());
        sessions.add(session);
    }

    public void send(Message message) {
        sessions.stream().forEach(session ->
                session.getAsyncRemote().sendBinary(wrap(message.toByteArray())));
    }

    @OnMessage
    public void onMessage(byte[] byteBuffer) {
        Message message;
        try {
            message = Message.parseFrom(byteBuffer);
            switch (message.getType()) {
                case VALIDATE_CARD_REQUEST:
                    logger.info("Received message : {} ", message.getValidateCardRequest());
                    validateCardRequestReceiver.handle(message.getValidateCardRequest());
                    break;
                case VALIDATE_PIN_REQUEST:
                    logger.info("Received message : {} ", message.getValidatePinRequest());
                    validatePinRequestReceiver.handle(message.getValidatePinRequest());
                    break;
                case DEPOSIT_REQUEST:
                    logger.info("Received message : {} ", message.getDepositRequest());
                    depositRequestReceiver.handle(message.getDepositRequest());
                    break;
                case INQUIRY_REQUEST:
                    logger.info("Received message : {} ", message.getInquiryRequest());
                    inquiryRequestReceiver.handle(message.getInquiryRequest());
                    break;
                default:
                    logger.info("Discard unknown {} message type", message.getType());
            }
        } catch (InvalidProtocolBufferException e) {
            propagate(e);
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        sessions.remove(session);
        logger.info("Session ID : {} - Connection closed with status code {} and reason {}",
                session.getId(),
                closeReason.getCloseCode(),
                closeReason.getReasonPhrase()
        );
    }

    @OnError
    public void onError(Session session, Throwable cause) {
        sessions.remove(session);
        logger.info("Session ID : {} - Connection error : {}", session.getId(), cause);
    }
}
