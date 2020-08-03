import {Component, ElementRef, Inject, ViewChild} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {
    CollectionService,
    RestMediaCollection,
    RestMediaItem,
    RestTaskDescription,
    TaskGroup,
    TaskType,
    VideoItem
} from '../../../../../openapi';
import {FormControl, FormGroup} from '@angular/forms';
import {Observable} from 'rxjs';
import {first} from 'rxjs/operators';
import {AppConfig} from '../../../app.config';
import {CompetitionFormBuilder} from './competition-form.builder';


/**
 * Its expected that the taskGroup and taskType properties are correctly given
 * even in the case this is 'edit'!
 */
export interface CompetitionBuilderTaskDialogData {
    taskGroup: TaskGroup;
    taskType: TaskType;
    task?: RestTaskDescription;
}


@Component({
    selector: 'app-competition-builder-task-dialog',
    templateUrl: './competition-builder-task-dialog.component.html'
})
export class CompetitionBuilderTaskDialogComponent {
    form: FormGroup;
    units = ['FRAME_NUMBER', 'SECONDS', 'MILLISECONDS', 'TIMECODE'];

    /** Data source for list of {@link MediaCollection}. Loaded upon construction of the dialog. */
    mediaCollectionSource: Observable<RestMediaCollection[]>;

    /** The {@link CompetitionFormBuilder} used by this dialogue. */
    builder: CompetitionFormBuilder;

    showPlayer = false;
    videoUrl: Observable<string>;
    @ViewChild('videoPlayer', {static: false}) video: ElementRef;

    constructor(public dialogRef: MatDialogRef<CompetitionBuilderTaskDialogComponent>,
                public collectionService: CollectionService,
                @Inject(MAT_DIALOG_DATA) public data: CompetitionBuilderTaskDialogData,
                public config: AppConfig) {

        this.builder = new CompetitionFormBuilder(this.data.taskGroup, this.data.taskType, this.collectionService, this.data.task);
        this.form = this.builder.form;
        this.mediaCollectionSource = this.collectionService.getApiCollectionList();
    }

    /**
     *
     */
    public addQueryTarget() {

    }

    /**
     * Handler for + button for query hint form component.
     */
    public addQueryComponent(componentType: TaskType.ComponentsEnum) {
        this.builder.addComponentForm(componentType);
    }

    /**
     * Handler for (-) button for query hint form components.
     *
     * @param index The index to remove the component at
     */
    public removeQueryComponent(index: number) {
        this.builder.removeComponentForm(index);
    }

    /**
     * Converts a MediaItem to its display value for the autocomplete field.
     *
     * @param value MediaItem to convert
     */
    public mediaItemToDisplay(value: RestMediaItem) {
        if (value) {
            return `${value.name} (${value.type})`;
        } else {
            return '';
        }
    }

    /**
     * Handler for 'save' button.
     */
    public save() {
        if (this.form.valid) {
            this.dialogRef.close(this.builder.fetchFormData());
        }
    }

    /**
     * The form data as json
     */
    asJson(): string {
        return JSON.stringify(this.builder.fetchFormData());
    }

    /**
     * Prints the JSONified form data to console
     */
    export() {
        console.log(this.asJson());
    }

    /**
     * Picks a ranomd {@link MediaItem} from the list.
     *
     * @param collectionId The ID of the collection to pick a {@link MediaItem} from.
     * @param target The target {@link FormControl} to apply the value to.
     */
    public pickRandomMediaItem(collectionId: string, target: FormControl) {
        this.collectionService.getApiCollectionWithCollectionidRandom(collectionId).pipe(first()).subscribe(value => {
            target.setValue(value);
        });
    }

    /**
     * Picks a random segment within the given {@link MediaItem} .
     *
     * @param item The {@link VideoItem} to pick the segment for.
     * @param startControl The target {@link FormControl} to apply the value to.
     * @param endControl The target {@link FormControl} to apply the value to.
     * @param unitControl The target {@link FormControl} to apply the value to.
     */
    public pickRandomSegment(item: VideoItem, startControl: FormControl, endControl: FormControl, unitControl: FormControl) {
        const start = this.randInt(1, (item.durationMs / 1000) / 2); // always in first half
        let end = 1;
        do {
            end = start + this.randInt(5, (item.durationMs / 1000)); // Arbitrary 5 seconds minimal length
        } while (end > (item.durationMs / 1000));
        startControl.setValue(start);
        endControl.setValue(end);
        unitControl.setValue('SECONDS');
    }

    toggleVideoPlayer() {
        if (this.showPlayer) {
            if (this.video && this.video.nativeElement) {
                const player = this.video.nativeElement as HTMLVideoElement;
                if (!player.paused) {
                    player.pause();
                }
            }
            this.videoUrl = null;
        } else {
            const url = this.pathForItem(this.form.get('mediaItemId').value as VideoItem);
            this.videoUrl = new Observable<string>(sub => sub.next(url));
        }
        this.showPlayer = !this.showPlayer;
    }

    /**
     * Handler for 'close' button.
     */
    public close(): void {
        this.dialogRef.close(null);
    }

    /**
     * Handler for 'close' button.
     */
    private pathForItem(item: VideoItem): string {
        // units = ['FRAME_NUMBER', 'SECONDS', 'MILLISECONDS', 'TIMECODE'];
        let timeSuffix = '';
        switch (this.form.get('time_unit').value) {
            case 'FRAME_NUMBER':
                const start = Math.round(this.form.get('start').value / item.fps);
                const end = Math.round(this.form.get('end').value / item.fps);
                timeSuffix = `#t=${start},${end}`;
                break;
            case 'SECONDS':
                timeSuffix = `#t=${this.form.get('start').value},${this.form.get('end').value}`;
                break;
            case 'MILLISECONDS':
                timeSuffix = `#t=${Math.round(this.form.get('start').value / 1000)},${Math.round(this.form.get('end').value / 1000)}`;
                break;
            case 'TIMECODE':
                console.log('Not yet supported'); // TODO make it!
                break;
            default:
                console.error(`The time unit ${this.form.get('time_unit').value} is not supported`);
        }
        return "";
    }

    private randInt(min: number, max: number): number {
        min = Math.floor(min);
        max = Math.ceil(max);
        return Math.round(Math.random() * (max - min + 1) + min);
    }
}
