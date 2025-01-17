import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

import { VideoPlayerSegmentBuilderComponent } from './video-player-segment-builder.component';

describe('VideoPlayerSegmentBuilderComponent', () => {
  let component: VideoPlayerSegmentBuilderComponent;
  let fixture: ComponentFixture<VideoPlayerSegmentBuilderComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [VideoPlayerSegmentBuilderComponent],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(VideoPlayerSegmentBuilderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
