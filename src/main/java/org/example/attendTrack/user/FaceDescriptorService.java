package org.example.attendTrack.user;

import lombok.RequiredArgsConstructor;
import org.example.attendTrack.common.exception.ApiException;
import org.example.attendTrack.common.exception.ErrorCode;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FaceDescriptorService {

    private final FaceDescriptorRepository faceDescriptorRepository;
    private final UserRepository userRepository;

    // Cosine similarity threshold for 512-dim MobileFaceNet embeddings.
    // Similarity >= 0.45 is considered the same person.
    private static final double FACE_SIMILARITY_THRESHOLD = 0.45;
    private static final int EXPECTED_DESCRIPTOR_LENGTH = 512;

    private static double cosineSimilarity(double[] a, double[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot   += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    @Transactional
    public void save(UUID userId, double[] descriptor) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "User not found: " + userId));

        if (user.getRole() != Role.WORKER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.WRONG_ROLE, "Face descriptors are only supported for workers");
        }

        if (descriptor.length != EXPECTED_DESCRIPTOR_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "Invalid descriptor length: expected " + EXPECTED_DESCRIPTOR_LENGTH + ", got " + descriptor.length);
        }

        List<FaceDescriptor> others = faceDescriptorRepository.findAllExcludingUser(userId);
        for (FaceDescriptor other : others) {
            if (cosineSimilarity(descriptor, other.getDescriptor()) >= FACE_SIMILARITY_THRESHOLD) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.FACE_ALREADY_REGISTERED,
                        "This face is already registered to another worker");
            }
        }

        faceDescriptorRepository.findByUserId(userId)
                .ifPresentOrElse(
                        existing -> {
                            existing.updateDescriptor(descriptor);
                            faceDescriptorRepository.save(existing);
                        },
                        () -> faceDescriptorRepository.save(
                                FaceDescriptor.builder()
                                        .user(user)
                                        .descriptor(descriptor)
                                        .build()
                        )
                );
    }

    @Transactional
    public void delete(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "User not found: " + userId);
        }
        faceDescriptorRepository.deleteByUserId(userId);
    }
}
