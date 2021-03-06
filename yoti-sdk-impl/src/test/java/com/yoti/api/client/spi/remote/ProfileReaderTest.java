package com.yoti.api.client.spi.remote;

import static com.yoti.api.client.spi.remote.util.CryptoUtil.encryptSymmetric;
import static com.yoti.api.client.spi.remote.util.CryptoUtil.generateSymmetricKey;

import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.util.Arrays;

import com.yoti.api.client.Attribute;
import com.yoti.api.client.Profile;
import com.yoti.api.client.ProfileException;
import com.yoti.api.client.spi.remote.proto.AttrProto;
import com.yoti.api.client.spi.remote.proto.AttributeListProto;
import com.yoti.api.client.spi.remote.proto.EncryptedDataProto;
import com.yoti.api.client.spi.remote.util.CryptoUtil;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.hamcrest.core.IsCollectionContaining;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProfileReaderTest {

    private static final String STRING_ATTRIBUTE_NAME = "testStringAttr";
    private static final AttrProto.Attribute STRING_ATTRIBUTE_PROTO = AttrProto.Attribute.newBuilder()
            .setName(STRING_ATTRIBUTE_NAME)
            .build();
    private static final byte[] PROFILE_DATA_BYTES = AttributeListProto.AttributeList.newBuilder()
            .addAttributes(STRING_ATTRIBUTE_PROTO)
            .build()
            .toByteArray();

    @InjectMocks ProfileReader testObj;

    @Mock AttributeListConverter attributeListConverterMock;

    CryptoUtil.EncryptionResult validProfileEncryptionResult;
    Key receiptKey;

    @Before
    public void setUp() throws Exception {
        receiptKey = generateSymmetricKey();
        validProfileEncryptionResult = encryptSymmetric(PROFILE_DATA_BYTES, receiptKey);
    }

    @Test
    public void shouldWrapInvalidProtocolBufferException() {
        byte[] invalidProfileContent = new byte[] { 1, 2, 3 };

        try {
            testObj.read(invalidProfileContent, receiptKey);
        } catch (ProfileException e) {
            assertThat(e.getMessage(), containsString("Cannot decode profile"));
            assertTrue(e.getCause() instanceof InvalidProtocolBufferException);
            return;
        }
        fail("Expected an Exception");
    }

    @Test
    public void shouldFailWithNoIV() {
        byte[] profileContent = EncryptedDataProto.EncryptedData.newBuilder()
                .setCipherText(ByteString.copyFrom(validProfileEncryptionResult.data))
                .build()
                .toByteArray();

        try {
            testObj.read(profileContent, receiptKey);
        } catch (ProfileException e) {
            assertThat(e.getMessage(), containsString("Receipt key IV must not be null."));
            return;
        }
        fail("Expected an Exception");
    }

    @Test
    public void shouldWrapExcetionFromInvalidIV() {
        byte[] profileContent = EncryptedDataProto.EncryptedData.newBuilder()
                .setCipherText(ByteString.copyFrom(validProfileEncryptionResult.data))
                .setIv(ByteString.copyFrom(new byte[] { 1, 2 }))
                .build()
                .toByteArray();

        try {
            testObj.read(profileContent, receiptKey);
        } catch (ProfileException e) {
            assertThat(e.getMessage(), containsString("Error decrypting data"));
            assertTrue(e.getCause() instanceof GeneralSecurityException);
            return;
        }
        fail("Expected an Exception");
    }

    @Test
    public void shouldWrapExceptionFromHavingNoEncryptedData() {
        byte[] profileContent = EncryptedDataProto.EncryptedData.newBuilder()
                .setIv(ByteString.copyFrom(validProfileEncryptionResult.iv))
                .build()
                .toByteArray();

        try {
            testObj.read(profileContent, receiptKey);
        } catch (ProfileException e) {
            assertThat(e.getMessage(), containsString("Error decrypting data"));
            assertTrue(e.getCause() instanceof GeneralSecurityException);
            return;
        }
        fail("Expected an Exception");
    }

    @Test
    public void shouldWrapExcetionFromInvalidEncryptedData() {
        byte[] profileContent = EncryptedDataProto.EncryptedData.newBuilder()
                .setCipherText(ByteString.copyFrom(new byte[] { 1, 2 }))
                .setIv(ByteString.copyFrom(validProfileEncryptionResult.iv))
                .build()
                .toByteArray();

        try {
            testObj.read(profileContent, receiptKey);
        } catch (ProfileException e) {
            assertThat(e.getMessage(), containsString("Error decrypting data"));
            assertTrue(e.getCause() instanceof GeneralSecurityException);
            return;
        }
        fail("Expected an Exception");
    }

    @Test
    public void shouldGetEmptyProfileWhenGivenNullContent() throws Exception {
        Profile result = testObj.read(null, receiptKey);

        assertThat(result.getAttributes(), hasSize(0));
    }

    @Test
    public void shouldDecodeProfileCorrectly() throws Exception {
        Attribute<String> stringAttribute = new SimpleAttribute<>("someName", "someValue");
        byte[] profileContent = EncryptedDataProto.EncryptedData.newBuilder()
                .setCipherText(ByteString.copyFrom(validProfileEncryptionResult.data))
                .setIv(ByteString.copyFrom(validProfileEncryptionResult.iv))
                .build()
                .toByteArray();
        when(attributeListConverterMock.parseAttributeList(PROFILE_DATA_BYTES)).thenReturn(Arrays.<Attribute<?>>asList(stringAttribute));

        Profile result = testObj.read(profileContent, receiptKey);

        assertThat(result.getAttributes(), hasSize(1));
        assertThat(result.getAttributes(), IsCollectionContaining.hasItem(stringAttribute));
    }

}
