import {Component, ElementRef, Inject, ViewChild} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialog, MatDialogRef} from '@angular/material/dialog';
import {
    CollectionService,
    MediaCollection,
    MediaItem,
    RestTaskDescription,
    RestTaskDescriptionComponent,
    RestTaskDescriptionTarget,
    TaskGroup,
    TaskType,
    TemporalRange,
    VideoItem
} from '../../../../../openapi';
import {FormArray, FormControl, FormGroup, Validators} from '@angular/forms';
import {Observable} from 'rxjs';
import {filter, tap} from 'rxjs/operators';
import {AppConfig} from '../../../app.config';
import {CompetitionBuilderTaskDescriptionComponentDialogComponent} from '../competition-builder-task-description-component-dialog/competition-builder-task-description-component-dialog.component';


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
    mediaCollectionSource: Observable<MediaCollection[]>;
    mediaCollections: MediaCollection[];
    mediaItemSource: Observable<MediaItem[]>;
    showPlayer = false;
    videoUrl: Observable<string>;
    @ViewChild('videoPlayer', {static: false}) video: ElementRef;

    /**
     * Convenience access
     */
    taskType: TaskType;

    constructor(public dialogRef: MatDialogRef<CompetitionBuilderTaskDialogComponent>,
                public collectionService: CollectionService,
                @Inject(MAT_DIALOG_DATA) public data: CompetitionBuilderTaskDialogData,
                public config: AppConfig,
                private dialog: MatDialog) {

        this.taskType = data?.taskType;

        this.mediaCollectionSource = this.collectionService.getApiCollection().pipe(tap(x => this.mediaCollections = x));

        this.form = new FormGroup({
            name: new FormControl(this?.data?.task?.name, [Validators.required]),
            duration: new FormControl(this?.data.taskType.taskDuration, [Validators.required, Validators.min(1)]),
            group: new FormControl(this?.data?.taskGroup.name, [Validators.required]),
            type: new FormControl(this?.data?.taskType.name, [Validators.required]),
            components: new FormArray(this?.data?.task?.components ? this?.data?.task?.components.map((v) => new FormControl(v)) : [], [Validators.required, Validators.minLength(1)]),
            collection: new FormControl(this?.data?.task?.defaultMediaCollectionId, [Validators.required]),
        });

        this.form.addControl('target.type', new FormControl(this.taskType.targetType, [Validators.required]));
        this.form.addControl('target.items', new FormArray(this?.data?.task?.target?.mediaItems ? this?.data?.task?.target?.mediaItems.map((v) => new FormControl(v)) : [], [Validators.required, Validators.minLength(1)]));
        this.form.addControl('target.range.start', new FormControl(this?.data?.task?.target?.range?.start, [Validators.required, Validators.min(0)]));
        this.form.addControl('target.range.end', new FormControl(this?.data?.task?.target?.range?.end, [Validators.required, Validators.min(0)]));

        if (this?.data?.task?.duration) {
            /* in case of editing, default is from type */
            this.form.get('duration').setValue(this.data.task.duration);
        }

        // switch (this.data.taskGroup.type) {
        //     case 'KIS_VISUAL':
        //         this.form = CompetitionBuilderTaskDialogComponent.KisVisualFormControl(this.data.taskGroup, this.data.task as KisVisualTaskDescription);
        //         this.mediaItemSource = this.form.get('mediaItemId').valueChanges.pipe(
        //             filter((value: string) => value.length >= 1),
        //             flatMap(value => {
        //                 return this.collectionService.getApiCollectionWithCollectionidWithStartswith(this.form.get('mediaCollection').value, value);
        //             })
        //         );
        //         break;
        //     case 'KIS_TEXTUAL':
        //         this.form = CompetitionBuilderTaskDialogComponent.KisTextualFormControl(this.data.taskGroup, this.data.task as KisTextualTaskDescription);
        //         this.mediaItemSource = this.form.get('mediaItemId').valueChanges.pipe(
        //             filter((value: string) => value.length >= 1),
        //             flatMap((value) => {
        //                 return this.collectionService.getApiCollectionWithCollectionidWithStartswith(this.form.get('mediaCollection').value, value);
        //             })
        //         );
        //         break;
        //     case 'AVS':
        //         this.form = CompetitionBuilderTaskDialogComponent.AvsFormControl(this.data.taskGroup, this.data.task as AvsTaskDescription);
        //         break;
        // }
    }

    // /**
    //  * Prepares and initializes the FormControl for an KIS Textual Task Description.
    //  *
    //  * @param taskGroup The task group the new task should belong to.
    //  * @param task The task item (optional)
    //  */
    // public static KisVisualFormControl(taskGroup: TaskGroup, task?: KisVisualTaskDescription) {
    //     const addTo = this.BasicFormControl(taskGroup, task);
    //
    //     addTo.addControl('mediaCollection', new FormControl(task?.item.collection, [Validators.required]));
    //     addTo.addControl('mediaItemId', new FormControl(task?.item, [Validators.required, Validators.min(1)]));
    //     addTo.addControl('start', new FormControl(task?.temporalRange.start.value, [Validators.required, Validators.min(0)]));
    //     addTo.addControl('end', new FormControl(task?.temporalRange.end.value, [Validators.required, Validators.min(0)]));
    //     addTo.addControl('time_unit', new FormControl(task?.temporalRange.start.unit ? task.temporalRange.start.unit : 'FRAME_NUMBER', [Validators.required, Validators.min(0)]));
    //     return addTo;
    // }
    //
    // /**
    //  * Prepares and initializes the FormControl for an KIS Textual Task Description.
    //  *
    //  * @param taskGroup The task group the new task should belong to.
    //  * @param task The task item (optional)
    //  */
    // public static KisTextualFormControl(taskGroup: TaskGroup, task?: KisTextualTaskDescription) {
    //     const addTo = this.BasicFormControl(taskGroup, task);
    //     addTo.addControl('mediaCollection', new FormControl(task?.item.collection, [Validators.required]));
    //     addTo.addControl('mediaItemId', new FormControl(task?.item, [Validators.required, Validators.min(1)]));
    //     addTo.addControl('start', new FormControl(task?.temporalRange.start.value, [Validators.required, Validators.min(0)]));
    //     addTo.addControl('end', new FormControl(task?.temporalRange.end.value, [Validators.required, Validators.min(0)]));
    //     addTo.addControl('time_unit', new FormControl(task?.temporalRange.start.unit ? task.temporalRange.start.unit : 'FRAME_NUMBER', [Validators.required, Validators.min(0)]));
    //     if (task != null) {
    //         addTo.addControl('descriptions', new FormArray(task.descriptions.map((v) => new FormControl(v, [Validators.minLength(1), Validators.required]))));
    //     } else {
    //         addTo.addControl('descriptions', new FormArray([new FormControl('', [Validators.minLength(1), Validators.required])]));
    //     }
    //     addTo.addControl('delay', new FormControl(task?.delay ? task.delay : 30, [Validators.required, Validators.min(0)]));
    //     return addTo;
    // }
    //
    // /**
    //  * Prepares and initializes the FormControl for an AVS Task Description.
    //  *
    //  * @param taskGroup The task group the new task should belong to.
    //  * @param task The task item (optional)
    //  */
    // public static AvsFormControl(taskGroup: TaskGroup, task?: AvsTaskDescription) {
    //     const addTo = this.BasicFormControl(taskGroup, task);
    //     addTo.addControl('mediaCollection', new FormControl(task?.defaultCollection));
    //     addTo.addControl('description', new FormControl(task?.description, Validators.minLength(1)));
    //     return addTo;
    // }

    /**
     * Handler for + button for task description component. Adds said component
     */
    public addDescComponent() {
        const dialogRef = this.dialog.open(
            CompetitionBuilderTaskDescriptionComponentDialogComponent,
            {data: {}, width: '650px'}
        );
        dialogRef.afterClosed().pipe(
            filter(g => g != null)
        ).subscribe((g: RestTaskDescriptionComponent) => {
            (this.form.get('components') as FormArray).push(new FormControl(g));
        });
    }

    /**
     * Handler for (-) button for task description components. Removes said component
     * @param index The index to remove the component at
     */
    public removeDescComponent(index: number) {
        (this.form.get('components') as FormArray).removeAt(index);
    }

    /**
     * Handler for + button for task descriptions (KIS_TEXTUAL tasks only). Adds a description.
     *
     * @param index The index to add description at.
     */
    public addDescription(index: number) {
        (this.form.get('descriptions') as FormArray).insert(index, new FormControl('', Validators.minLength(1)));
    }

    /**
     * Handler for (-) button for task descriptions (KIS_TEXTUAL tasks only). Removes a description.
     *
     * @param index The index to remove description at.
     */
    public removeDescription(index: number) {
        (this.form.get('descriptions') as FormArray).removeAt(index);
    }

    /**
     * Converts a MediaItem to its display value for the autocomplete field.
     *
     * @param value MediaItem to convert
     */
    public mediaItemToDisplay(value: MediaItem) {
        if (value) {
            return `${value.name} (${value.id})`;
        } else {
            return '';
        }
    }

    /**
     * Handler for 'save' button.
     */
    public save() {
        if (this.form.valid) {
            this.dialogRef.close(this.fetchFormData());
        }
    }

    /**
     * Fetches the form data and transforms it to the return type
     */
    fetchFormData(): RestTaskDescription {
        const data = {
            id: 'TODO:serverside-only?', // FIXME isn't this serverside only or is this a prep for UUID all the things?
            name: this.form.get('name').value,
            taskGroup: this.form.get('group').value,
            taskType: this.form.get('type').value,
            duration: this.form.get('duration').value,
            defaultMediaCollectionId: this.form.get('collection').value,
            components: this.form.get('components').value,
            target: {
                type: this.taskType.targetType,
                mediaItems: this.form.get('target.items').value
            } as RestTaskDescriptionTarget
        } as RestTaskDescription;
        if (this.form.get('target.range.start') && this.form.get('target.range.end')) {
            data.target.range = {
                start: this.form.get('target.range.start').value,
                end: this.form.get('target.range.end').value
            } as TemporalRange;
        }
        return data;
    }

    /**
     * The form data as json
     */
    asJson(): string {
        return JSON.stringify(this.fetchFormData());
    }

    /**
     * Prints the JSONified form data to console
     */
    export() {
        console.log(this.asJson());
    }

    randomisedMediaItem() {
        this.collectionService.getApiCollectionRandomWithCollectionid(
            (this.form.get('mediaCollection') as FormControl).value as number)
            .subscribe(value => {
                this.form.get('mediaItemId').setValue(value);
            });
    }

    randomiseSegment() {
        // TODO rework with #122
        const item = this.form.get('mediaItemId').value as VideoItem;
        const start = this.randInt(1, (item.durationMs / 1000) / 2); // always in first half
        let end = 1;
        do {
            end = start + this.randInt(5, (item.durationMs / 1000)); // Arbitrary 5 seconds minimal length
        } while (end > (item.durationMs / 1000));
        this.form.get('time_unit').setValue('SECONDS');
        this.form.get('start').setValue(start);
        this.form.get('end').setValue(end);
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
            const url = this.resolvePath(this.form.get('mediaItemId').value as VideoItem);
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

    private resolvePath(item: VideoItem): string {
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
        const collection = this.mediaCollections.find(x => x.id === this.form.get('mediaCollection').value).name;
        return this.config.resolveApiUrl(`/media/${collection}/${item.name}${timeSuffix}`);
    }

    private rand(min: number, max: number): number {
        return Math.random() * (max - min) + min;
    }

    private randInt(min: number, max: number): number {
        min = Math.floor(min);
        max = Math.ceil(max);
        return Math.round(Math.random() * (max - min + 1) + min);
    }

    // private getTaskDescription(): TaskDescriptionBase {
    //     switch (this.data.taskGroup.type) {
    //         case 'AVS':
    //             return {
    //                 name: this.form.get('name').value,
    //                 taskType: this.data.taskGroup.type,
    //                 taskGroup: this.data.taskGroup,
    //                 duration: this.form.get('duration').value,
    //                 defaultCollection: this.form.get('mediaCollection').value,
    //                 description: this.form.get('description').value
    //             } as AvsTaskDescription;
    //         case 'KIS_TEXTUAL':
    //             return {
    //                 name: this.form.get('name').value,
    //                 taskType: this.data.taskGroup.type,
    //                 taskGroup: this.data.taskGroup,
    //                 duration: this.form.get('duration').value,
    //                 item: (this.form.get('mediaItemId').value) as VideoItem,
    //                 temporalRange: {
    //                     start: {
    //                         value: this.form.get('start').value,
    //                         unit: this.form.get('time_unit').value
    //                     } as TemporalPoint,
    //                     end: {
    //                         value: this.form.get('end').value,
    //                         unit: this.form.get('time_unit').value
    //                     } as TemporalPoint
    //                 } as TemporalRange,
    //                 descriptions: (this.form.get('descriptions') as FormArray).value,
    //                 delay: this.form.get('delay').value
    //             } as KisTextualTaskDescription;
    //         case 'KIS_VISUAL':
    //             return {
    //                 name: this.form.get('name').value,
    //                 taskType: this.data.taskGroup.type,
    //                 taskGroup: this.data.taskGroup,
    //                 duration: this.form.get('duration').value,
    //                 item: (this.form.get('mediaItemId').value) as VideoItem,
    //                 temporalRange: {
    //                     start: {
    //                         value: this.form.get('start').value,
    //                         unit: this.form.get('time_unit').value
    //                     } as TemporalPoint,
    //                     end: {
    //                         value: this.form.get('end').value,
    //                         unit: this.form.get('time_unit').value
    //                     } as TemporalPoint
    //                 } as TemporalRange,
    //             } as KisVisualTaskDescription;
    //     }
    // }
}