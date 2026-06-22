-- V2: Clear all 128-dim face descriptors (face-api.js).
-- Workers must re-enroll using the new 512-dim MobileFaceNet pipeline.
DELETE FROM face_descriptors;
