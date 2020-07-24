import {NgModule} from '@angular/core';
import {MatTableModule} from '@angular/material/table';
import {MatIconModule} from '@angular/material/icon';
import {MatButtonModule} from '@angular/material/button';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatDialogModule} from '@angular/material/dialog';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {CommonModule} from '@angular/common';
import {MatListModule} from '@angular/material/list';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatMenuModule} from '@angular/material/menu';
import {CompetitionBuilderComponent} from './competition-builder.component';
import {CompetitionBuilderTeamDialogComponent} from './competition-builder-team-dialog/competition-builder-team-dialog.component';
import {CompetitionBuilderTaskDialogComponent} from './competition-builder-task-dialog/competition-builder-task-dialog.component';
import {MatAutocompleteModule} from '@angular/material/autocomplete';
import {MatSelectModule} from '@angular/material/select';
import {CompetitionBuilderTaskGroupDialogComponent} from './competition-builder-task-group-dialog/competition-builder-task-group.component';
import {MatChipsModule} from '@angular/material/chips';
import {CompetitionBuilderTaskTypeDialogComponent} from './competition-builder-task-type-dialog/competition-builder-task-type-dialog.component';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {CompetitionBuilderTaskDescriptionComponentDialogComponent} from './competition-builder-task-description-component-dialog/competition-builder-task-description-component-dialog.component';
import {CompetitionBuilderLegacyTaskDialogComponent} from './competition-builder-legacy-task-dialog/competition-builder-legacy-task-dialog.component';

@NgModule({
    imports: [
        MatTableModule,
        MatIconModule,
        MatButtonModule,
        MatTooltipModule,
        MatDialogModule,
        MatFormFieldModule,
        MatInputModule,
        FormsModule,
        ReactiveFormsModule,
        CommonModule,
        MatListModule,
        MatProgressSpinnerModule,
        MatMenuModule,
        MatAutocompleteModule,
        MatSelectModule,
        MatChipsModule,
        MatCheckboxModule
    ],
    exports: [CompetitionBuilderComponent],
    declarations: [
        CompetitionBuilderComponent,
        CompetitionBuilderTeamDialogComponent,
        CompetitionBuilderTaskDialogComponent,
        CompetitionBuilderLegacyTaskDialogComponent,
        CompetitionBuilderTaskGroupDialogComponent,
        CompetitionBuilderTaskTypeDialogComponent,
        CompetitionBuilderTaskDescriptionComponentDialogComponent],
    providers: []
})
export class CompetitionBuilderModule {
}
