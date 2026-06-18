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

    private static final double FACE_DISTANCE_THRESHOLD = 0.45;

    private static double euclideanDistance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    @Transactional
    public void save(UUID userId, double[] descriptor) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "User not found: " + userId));

        if (user.getRole() != Role.WORKER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.WRONG_ROLE, "Face descriptors are only supported for workers");
        }

        List<FaceDescriptor> others = faceDescriptorRepository.findAllExcludingUser(userId);
        for (FaceDescriptor other : others) {
            if (euclideanDistance(descriptor, other.getDescriptor()) < FACE_DISTANCE_THRESHOLD) {
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
